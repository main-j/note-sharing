package com.project.login.service.remark;


import com.project.login.convert.RemarkConvert;
import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.*;
import com.project.login.model.dto.remark.RemarkDeleteDTO;
import com.project.login.model.dto.remark.RemarkInsertDTO;
import com.project.login.model.dto.remark.RemarkSelectByNoteDTO;
import com.project.login.model.vo.RemarkVO;
import com.project.login.model.vo.RemarkDetailVO;
import com.project.login.repository.RemarkLikeCountRepository;
import com.project.login.repository.RemarkLikeByUsersRepository;
import com.project.login.repository.RemarkRepository;
import com.project.login.service.notestats.NoteStatsService;
import com.project.login.service.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemarkService {
    private final RemarkRepository remarkRepository;
    private final RemarkLikeByUsersRepository remarkLikeByUsersRepository;
    private final RemarkConvert remarkConvert;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final NoteStatsService noteStatsService;
    private final RemarkLikeCountRepository remarkLikeCountRepository;
    private final RedisTemplate<String, Object> redisTemplate;


    private static final String STATS_KEY_PREFIX = "remark_stats:";
    private static final String USER_LIKE_KEY_PREFIX = "remark_user_like:";
    private static final String F_COUNT = "count";
    private static final String F_VERSION = "version";
    private static final String F_LAST_ACTIVITY = "last_activity_at";
    private static final Duration STATS_TTL = Duration.ofHours(2);

    private final String remarkIdKey = "remark:";
    private final String NoteIdKey = "note_id_of_remark_list:";
    private final String replyToIdKey = "reply_to:";

    private final RabbitTemplate rabbitTemplate;
    private final String LikeCountQueue = "remarkLikeCount.redis.queue";
    private final String LikeUsersQueue = "remarkLikeUsers.redis.queue";

    private String statsKey(String remarkId)    { return STATS_KEY_PREFIX + remarkId; }
    private String userLikeKey(String remarkId) { return USER_LIKE_KEY_PREFIX + remarkId; }
    
    private final NotificationService notificationService;
    private final com.project.login.controller.RemarkWebSocketController remarkWebSocketController;

    /**
     * 写/读路径前的缓存回填（read-through），借鉴 NoteStatsService.initRedisIfNeeded。
     * - 若 Redis stats Hash 不存在或为空，从 MongoDB 加载 count + version
     *   写入 Hash，同时回填 user_like Set。这样保证 like/cancel 不会从 0 重新计数
     *   而把 MongoDB 中已有的点赞数"腰斩"。
     * - 顺便刷新 TTL。
     */
    private void initStatsIfNeeded(String remarkId) {
        if (remarkId == null || remarkId.isEmpty()) return;

        String sKey = statsKey(remarkId);
        HashOperations<String, Object, Object> hops = redisTemplate.opsForHash();
        boolean needLoad = !Boolean.TRUE.equals(redisTemplate.hasKey(sKey)) || hops.size(sKey) == 0;

        if (needLoad) {
            RemarkCountDO db = remarkLikeCountRepository.findById(remarkId).orElse(null);
            long count = (db != null && db.getRemarkLikeCount() != null) ? db.getRemarkLikeCount() : 0L;
            long version = (db != null && db.getVersion() != null) ? db.getVersion() : 0L;

            hops.put(sKey, F_COUNT, String.valueOf(count));
            hops.put(sKey, F_VERSION, String.valueOf(version));
            hops.put(sKey, F_LAST_ACTIVITY, LocalDateTime.now().toString());
        }
        redisTemplate.expire(sKey, STATS_TTL);

        String uKey = userLikeKey(remarkId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(uKey))) {
            remarkLikeByUsersRepository.findById(remarkId).ifPresent(rec -> {
                Set<Long> users = rec.getUserList();
                if (users != null && !users.isEmpty()) {
                    redisTemplate.opsForSet().add(uKey, users.toArray());
                }
            });
        }
        redisTemplate.expire(uKey, STATS_TTL);
    }

    private long parseLongSafe(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); }
        catch (NumberFormatException e) { return 0L; }
    }

    private String normalizeRemarkId(Object raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.toString().trim();
        if (id.length() >= 2 && id.startsWith("\"") && id.endsWith("\"")) {
            id = id.substring(1, id.length() - 1);
        }
        return id.isEmpty() ? null : id;
    }

    private RemarkDO loadRemarkDo(String remarkId, Long noteId) {
        if (remarkId == null || remarkId.isBlank()) {
            return null;
        }
        String cacheKey = remarkIdKey + remarkId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof RemarkDO remarkDO) {
                redisTemplate.expire(cacheKey, Duration.ofMinutes(15));
                return remarkDO;
            }
        }

        RemarkDO fromDb = remarkRepository.findByRemarkId(remarkId).orElse(null);
        if (fromDb == null && noteId != null) {
            fromDb = remarkRepository.findRemarksByNoteIdAndIsReplyFalse(noteId).stream()
                    .filter(item -> remarkId.equals(item.get_id()))
                    .findFirst()
                    .orElse(null);
        }
        if (fromDb != null) {
            redisTemplate.opsForValue().set(cacheKey, fromDb);
            redisTemplate.expire(cacheKey, Duration.ofMinutes(15));
        }
        return fromDb;
    }

    private RemarkVO transferDO2VO(RemarkDO remarkDO, UserDO user) {
        RemarkVO cur = remarkConvert.toVO(remarkDO);
        Long loginUserId = user.getId();
        String remarkId = remarkDO.get_id();

        // ------ Redis 回填（stats Hash + user_like Set）------
        // 与 likeRemark / cancelLikeRemark 共用同一个 read-through 入口：
        // Redis 缺失时从 MongoDB 加载 count + version + userList，避免缓存过期把已有点赞清零。
        initStatsIfNeeded(remarkId);

        // ------ 是否已点赞 ------
        boolean liked = Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(userLikeKey(remarkId), loginUserId));

        // ------ 点赞数量（来自 stats Hash 的 count 字段）------
        long likedCount = parseLongSafe(
                redisTemplate.opsForHash().get(statsKey(remarkId), F_COUNT));

        cur.setLikedOrNot(liked);
        cur.setLikeCount(likedCount);

        // ------ 设置用户头像 ------
        if (remarkDO.getUserId() != null) {
            try {
                UserDO commentAuthor = userMapper.selectById(remarkDO.getUserId());
                if (commentAuthor != null && commentAuthor.getAvatarUrl() != null) {
                    cur.setAvatarUrl(commentAuthor.getAvatarUrl());
                } else {
                    // 如果没有头像，设置为默认值或空字符串
                    cur.setAvatarUrl(null);
                }
            } catch (Exception e) {
                log.warn("获取评论用户头像失败，userId: {}", remarkDO.getUserId(), e);
                cur.setAvatarUrl(null);
            }
        }

        return cur;
    }

    /**
     * 递归构建评论回复树（支持无限层级）
     * @param parentId 父评论ID
     * @param user 当前登录用户
     * @return 子评论列表（树形结构）
     */
    private List<RemarkVO> buildReplyTree(String parentId, UserDO user) {
        if (parentId == null) {
            return new ArrayList<>();
        }

        // 查询所有直接子回复（isReply = true 且 parentId = 当前节点）
        List<RemarkDO> children = remarkRepository.findRemarksByParentIdAndIsReplyTrue(parentId);

        // 按时间排序
        children.sort((r1, r2) -> {
            String t1 = r1.getCreatedAt() != null ? r1.getCreatedAt() : "";
            String t2 = r2.getCreatedAt() != null ? r2.getCreatedAt() : "";
            return t1.compareTo(t2);
        });

        List<RemarkVO> childVOList = new ArrayList<>();
        for (RemarkDO child : children) {
            if (child == null || child.get_id() == null) continue;

            RemarkDO childDO = loadRemarkDo(child.get_id(), child.getNoteId());
            if (childDO == null) {
                childDO = child;
            }

            // 当前节点转 VO
            RemarkVO childVO = transferDO2VO(childDO, user);
            // 递归构建子节点
            List<RemarkVO> grandChildren = buildReplyTree(childDO.get_id(), user);
            childVO.setReplies(grandChildren);

            childVOList.add(childVO);
        }

        return childVOList;
    }

    @Transactional
    public List<RemarkVO> SelectRemark(RemarkSelectByNoteDTO remarkSelectByNoteDTO, Long loginUserId) {
        log.info(loginUserId.toString());
// 获取当前用户信息
        UserDO user = userMapper.selectById(loginUserId);
        log.info(user.toString());
        Long noteId = remarkSelectByNoteDTO.getNoteId();
        String noteKey = NoteIdKey + noteId;
        List<RemarkDO> firstLayerRemarks = new ArrayList<>();
        List<RemarkVO> result = new ArrayList<>();

// --- 一级评论列表处理 ---
        log.info("initialize complete");
        if (redisTemplate.hasKey(noteKey)) {
            List<Object> tmpList = redisTemplate.opsForList().range(noteKey, 0, -1);
            log.info("found in redis" + tmpList);
            redisTemplate.expire(noteKey, Duration.ofMinutes(15));
            if (tmpList != null) {
                for (Object rawId : tmpList) {
                    String remarkId = normalizeRemarkId(rawId);
                    RemarkDO curDO = loadRemarkDo(remarkId, noteId);
                    if (curDO != null) {
                        firstLayerRemarks.add(curDO);
                    }
                }
            }
        } else {
            log.info("finding firstLayer in mongodb");
            firstLayerRemarks = remarkRepository.findRemarksByNoteIdAndIsReplyFalse(noteId);
            log.info("get from mongodb,tmpRemarkDOList= " + firstLayerRemarks);
            if (!firstLayerRemarks.isEmpty()) {
                for (RemarkDO cur : firstLayerRemarks) {
                    if (cur != null && cur.get_id() != null) {
                        redisTemplate.opsForList().rightPush(noteKey, cur.get_id());
                        redisTemplate.opsForValue().set(remarkIdKey + cur.get_id(), cur);
                    }
                }
                redisTemplate.expire(noteKey, Duration.ofMinutes(15));
            }
            log.info("found the firstLayer");
        }

        for (RemarkDO curDO : firstLayerRemarks) {
            if (curDO == null || curDO.get_id() == null) {
                continue;
            }
            log.info(curDO.toString());
            RemarkVO curVO = transferDO2VO(curDO, user);
            curVO.setReplies(buildReplyTree(curDO.get_id(), user));
            result.add(curVO);
            log.info("show curVO" + curVO);
        }
        return result;
    }

    @Transactional
    public List<RemarkVO> selectRemarkByUserId(Long loginUserId) {
        List<RemarkVO> remarkVOList = new ArrayList<>();
        UserDO user=userMapper.selectById(loginUserId);
// 从数据库获取该用户所有一级评论
        List<RemarkDO> remarkDOList = remarkRepository.findByUserId(loginUserId);

        for (RemarkDO curDO : remarkDOList) {
            if (curDO == null || curDO.get_id() == null) continue;

            // --- 一级评论对象处理 ---
            String curKey = remarkIdKey + curDO.get_id();
            RemarkDO cachedCurDO = null;
            if (redisTemplate.hasKey(curKey)) {
                cachedCurDO = (RemarkDO) redisTemplate.opsForValue().get(curKey);
                redisTemplate.expire(curKey,Duration.ofMinutes(15));
            }
            if (cachedCurDO == null) {
                // Redis没有则使用数据库对象，并写入Redis
                cachedCurDO = curDO;
                redisTemplate.opsForValue().set(curKey, curDO);
                redisTemplate.expire(curKey,Duration.ofMinutes(15));
            }
            log.info(curDO.toString());
            // 转换DO为VO
            RemarkVO curVO = transferDO2VO(cachedCurDO, user);

            // --- 二级评论对象处理 ---
            List<String> remarkSecondLayerList = new ArrayList<>();
            String replyKey = replyToIdKey + curDO.get_id();
            if (redisTemplate.hasKey(replyKey)) {
                List<Object> tmpReplyList = redisTemplate.opsForList().range(replyKey, 0, -1);
                redisTemplate.expire(replyKey,Duration.ofMinutes(15));
                if (tmpReplyList != null) {
                    remarkSecondLayerList = tmpReplyList.stream()
                            .map(this::normalizeRemarkId)
                            .filter(Objects::nonNull)
                            .toList();
                }
            } else {
                // Redis没有则从数据库加载二级评论，并写入Redis
                List<RemarkDO> replyList = remarkRepository.findRemarksByParentIdAndIsReplyTrue(curDO.get_id());
                for (RemarkDO reply : replyList) {
                    if (reply != null && reply.get_id() != null) {
                        redisTemplate.opsForValue().set(remarkIdKey + reply.get_id(), reply);
                        redisTemplate.expire(remarkIdKey+reply.get_id(),Duration.ofMinutes(15));
                        remarkSecondLayerList.add(reply.get_id());
                    }
                }
            }

            // --- 构建二级评论VO ---
            List<RemarkVO> replies = new ArrayList<>();
            for (String replyId : remarkSecondLayerList) {
                RemarkDO replyDO = null;
                String replyRedisKey = remarkIdKey + replyId;
                if (redisTemplate.hasKey(replyRedisKey)) {
                    replyDO = (RemarkDO) redisTemplate.opsForValue().get(replyRedisKey);
                    redisTemplate.expire(replyRedisKey,Duration.ofMinutes(15));
                }
                if (replyDO == null) {
                    replyDO = remarkRepository.findByRemarkId(replyId).orElse(null);
                    if (replyDO != null) {
                        redisTemplate.opsForValue().set(replyRedisKey, replyDO);
                        redisTemplate.expire(replyRedisKey,Duration.ofMinutes(15));
                    }
                }
                if (replyDO != null) {
                    RemarkVO replyVO = transferDO2VO(replyDO, user);
                    replies.add(replyVO);
                }
            }
            curVO.setReplies(replies);

            // 将一级评论VO加入结果列表
            remarkVOList.add(curVO);
        }

        return remarkVOList;

    }

    @Transactional
    public Boolean insertRemark(RemarkInsertDTO remarkInsertDTO) {
        try {
            // 1. 转换 DTO 到 DO
            RemarkDO remarkDO = remarkConvert.toDO(remarkInsertDTO);
            remarkDO.setCreatedAt(LocalDateTime.now().toString());
            if(!remarkDO.getIsReply()){
                remarkDO.setReplyToUsername(null);
                remarkDO.setReplyToRemarkId(null);
                remarkDO.setParentId(null);
            }
            // 2. 保存到数据库
            remarkRepository.save(remarkDO);

            // 3. 删除相关缓存（第一次删除）
            if (!remarkDO.getIsReply()) {
                redisTemplate.delete(NoteIdKey + remarkDO.getNoteId());
            } else {
                redisTemplate.delete(replyToIdKey + remarkDO.getParentId());
            }

            // 4. 延时再删除一次（延时双删）
            new Thread(() -> {
                try {
                    Thread.sleep(50); // 延时 50ms，可根据实际并发调整
                    if (!remarkDO.getIsReply()) {
                        redisTemplate.delete(NoteIdKey + remarkDO.getNoteId());
                    } else {
                        redisTemplate.delete(replyToIdKey + remarkDO.getParentId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            noteStatsService.changeField(remarkDO.getNoteId(),"comments",1);

            // --- 创建通知 ---
            Long actorId = remarkInsertDTO.getUserId();
            Long noteId = remarkInsertDTO.getNoteId();
            if (Boolean.FALSE.equals(remarkDO.getIsReply())) {
                // 一级评论：评论我的笔记
                notificationService.createNoteCommentNotification(actorId, noteId);
            } else {
                // 回复评论：评论我的评论
                notificationService.createNoteReplyCommentNotification(remarkDO.getReplyToRemarkId(), actorId);
            }

            // --- 推送 WebSocket 消息 ---
            // 构建一个简化的 RemarkVO 用于推送（LikedOrNot 设为 false，前端接收后可根据当前用户设置）
            try {
                UserDO userDO = userMapper.selectById(actorId);
                if (userDO != null) {
                    RemarkVO remarkVO = transferDO2VO(remarkDO, userDO);
                    // 推送新评论到所有订阅该笔记的用户
                    remarkWebSocketController.broadcastNewRemark(noteId, remarkVO);
                }
            } catch (Exception e) {
                log.warn("推送评论 WebSocket 消息失败", e);
                // WebSocket 推送失败不影响评论插入
            }

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert remark", e);
        }
    }

    @Transactional
    public Boolean deleteRemark(RemarkDeleteDTO remarkDeleteDTO) {
        try {
            // 1. 查找要删除的评论
            RemarkDO remarkDO = remarkRepository.findByRemarkId(remarkDeleteDTO.getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Remark not found for ID: " + remarkDeleteDTO.getId()));
            Long noteId=remarkDO.getNoteId();
            // 2. 如果是接收的评论，先删除其子评论及点赞
            if (Boolean.FALSE.equals(remarkDO.getIsReply())) {
                String parentId = remarkDO.get_id();

                // 从 Redis 获取子评论列表
                String replyKey = replyToIdKey + parentId;
                List<String> childIds = new ArrayList<>();
                if (redisTemplate.hasKey(replyKey)) {
                    List<Object> tmpList = redisTemplate.opsForList().range(replyKey, 0, -1);
                    if (tmpList != null) {
                        childIds = tmpList.stream()
                                .map(this::normalizeRemarkId)
                                .filter(Objects::nonNull)
                                .toList();
                    }
                }

                // Redis 没有则从数据库加载
                if (childIds.isEmpty()) {
                    List<RemarkDO> childRemarks = remarkRepository.findRemarksByParentIdAndIsReplyTrue(parentId);
                    for (RemarkDO child : childRemarks) {
                        if (child != null && child.get_id() != null) {
                            childIds.add(child.get_id());
                        }
                    }
                }

                // 删除子评论点赞和缓存
                for (String childId : childIds) {
                    remarkLikeByUsersRepository.deleteById(childId);
                    remarkLikeCountRepository.deleteById(childId);
                    redisTemplate.delete(statsKey(childId));
                    redisTemplate.delete(userLikeKey(childId));
                    redisTemplate.delete(remarkIdKey + childId);
                    noteStatsService.changeField(noteId,"comments",-1);
                }

                redisTemplate.delete(replyKey);
                remarkRepository.deleteByParentId(parentId);
            }

            // 3. 删除该评论的点赞记录和数据库记录
            remarkLikeByUsersRepository.deleteById(remarkDO.get_id());
            remarkRepository.deleteById(remarkDO.get_id());
            redisTemplate.delete(statsKey(remarkDO.get_id()));
            redisTemplate.delete(userLikeKey(remarkDO.get_id()));
            // 4. 删除 Redis 缓存（第一次删除）
            redisTemplate.delete(remarkIdKey + remarkDO.get_id());
            if (!remarkDO.getIsReply()) {
                redisTemplate.delete(NoteIdKey + remarkDO.getNoteId());
            } else {
                redisTemplate.delete(replyToIdKey + remarkDO.getParentId());
            }

            // 5. 延时再删除一次缓存（延时双删）
            new Thread(() -> {
                try {
                    Thread.sleep(50); // 延时 50ms
                    redisTemplate.delete(remarkIdKey + remarkDO.get_id());
                    if (!remarkDO.getIsReply()) {
                        redisTemplate.delete(NoteIdKey + remarkDO.getNoteId());
                    } else {
                        redisTemplate.delete(replyToIdKey + remarkDO.getParentId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            noteStatsService.changeField(noteId,"comments",-1);
            return Boolean.TRUE;
        } catch (Exception e) {
            System.err.println("Failed to delete remark: " + e.getMessage());
            return Boolean.FALSE;
        }
    }

    /**
     * 递归收集所有子评论ID（包括子评论的子评论）
     * @param parentId 父评论ID
     * @param allChildIds 收集所有子评论ID的列表
     */
    private void collectAllChildIds(String parentId, List<String> allChildIds) {
        if (parentId == null || parentId.isEmpty()) {
            return;
        }

        // 从 Redis 获取直接子评论列表
        String replyKey = replyToIdKey + parentId;
        List<String> directChildIds = new ArrayList<>();
        if (redisTemplate.hasKey(replyKey)) {
            List<Object> tmpList = redisTemplate.opsForList().range(replyKey, 0, -1);
            if (tmpList != null) {
                directChildIds = tmpList.stream()
                        .map(this::normalizeRemarkId)
                        .filter(Objects::nonNull)
                        .toList();
            }
        }

        // Redis 没有则从数据库加载
        if (directChildIds.isEmpty()) {
            List<RemarkDO> childRemarks = remarkRepository.findRemarksByParentIdAndIsReplyTrue(parentId);
            for (RemarkDO child : childRemarks) {
                if (child != null && child.get_id() != null) {
                    directChildIds.add(child.get_id());
                }
            }
        }

        // 递归处理每个子评论
        for (String childId : directChildIds) {
            if (!allChildIds.contains(childId)) {
                allChildIds.add(childId);
                // 递归收集子评论的子评论
                collectAllChildIds(childId, allChildIds);
            }
        }
    }

    /**
     * 删除单个评论及其相关数据（不删除子评论）
     * @param remarkId 评论ID
     * @param noteId 笔记ID
     */
    private void deleteSingleRemark(String remarkId, Long noteId) {
        // 删除点赞记录和缓存
        remarkLikeByUsersRepository.deleteById(remarkId);
        remarkLikeCountRepository.deleteById(remarkId);
        redisTemplate.delete(statsKey(remarkId));
        redisTemplate.delete(userLikeKey(remarkId));
        redisTemplate.delete(remarkIdKey + remarkId);
        
        // 删除数据库记录
        remarkRepository.deleteById(remarkId);
        
        // 更新评论统计
        noteStatsService.changeField(noteId, "comments", -1);
    }

    /**
     * 管理员删除评论（跳过权限检查，可删除任何评论）
     * 递归删除所有子评论，但不影响父评论
     * @param remarkId 评论ID
     * @return 删除是否成功
     */
    @Transactional
    public Boolean adminDeleteRemark(String remarkId) {
        try {
            // 1. 查找要删除的评论
            RemarkDO remarkDO = remarkRepository.findByRemarkId(remarkId)
                    .orElseThrow(() -> new RuntimeException(
                            "Remark not found for ID: " + remarkId));
            Long noteId = remarkDO.getNoteId();
            
            // 2. 递归收集所有子评论ID（包括子评论的子评论）
            List<String> allChildIds = new ArrayList<>();
            collectAllChildIds(remarkId, allChildIds);

            // 3. 先删除所有子评论（从最深层开始删除，避免外键约束问题）
            // 由于MongoDB没有外键约束，可以按任意顺序删除，但为了逻辑清晰，我们从后往前删除
            for (String childId : allChildIds) {
                deleteSingleRemark(childId, noteId);
            }

            // 4. 删除子评论的Redis缓存键
            if (!allChildIds.isEmpty()) {
                String replyKey = replyToIdKey + remarkId;
                redisTemplate.delete(replyKey);
                // 删除所有子评论的replyKey
                for (String childId : allChildIds) {
                    redisTemplate.delete(replyToIdKey + childId);
                }
                // 批量删除子评论
                remarkRepository.deleteByParentId(remarkId);
                // 递归删除所有子评论
                for (String childId : allChildIds) {
                    remarkRepository.deleteByParentId(childId);
                }
            }

            // 5. 删除该评论本身
            deleteSingleRemark(remarkId, noteId);
            
            // 6. 删除 Redis 缓存（第一次删除）
            redisTemplate.delete(remarkIdKey + remarkId);
            if (!remarkDO.getIsReply()) {
                redisTemplate.delete(NoteIdKey + remarkDO.getNoteId());
            } else {
                redisTemplate.delete(replyToIdKey + remarkDO.getParentId());
            }

            // 7. 延时再删除一次缓存（延时双删）
            new Thread(() -> {
                try {
                    Thread.sleep(50); // 延时 50ms
                    redisTemplate.delete(remarkIdKey + remarkId);
                    if (!remarkDO.getIsReply()) {
                        redisTemplate.delete(NoteIdKey + remarkDO.getNoteId());
                    } else {
                        redisTemplate.delete(replyToIdKey + remarkDO.getParentId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return Boolean.TRUE;
        } catch (Exception e) {
            System.err.println("Failed to delete remark (admin): " + e.getMessage());
            e.printStackTrace();
            return Boolean.FALSE;
        }
    }


    @Transactional
    public Boolean likeRemark(String remarkId, Long userId) {
        log.info("starting like");
        // 1. 写路径 read-through：保证 Redis 与 MongoDB 已对齐，避免缓存缺失时把已有计数清零
        initStatsIfNeeded(remarkId);

        String uKey = userLikeKey(remarkId);
        // 2. Redis 是点赞状态的事实快照（initStatsIfNeeded 已确保它与 MongoDB 一致）
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(uKey, userId))) {
            return false;
        }

        redisTemplate.opsForSet().add(uKey, userId);
        redisTemplate.expire(uKey, STATS_TTL);

        // 3. 原子 HINCRBY count，并刷新 last_activity_at
        HashOperations<String, Object, Object> hops = redisTemplate.opsForHash();
        String sKey = statsKey(remarkId);
        Long newCount = hops.increment(sKey, F_COUNT, 1L);
        if (newCount != null && newCount < 0) {
            hops.put(sKey, F_COUNT, "0");
        }
        hops.put(sKey, F_LAST_ACTIVITY, LocalDateTime.now().toString());
        redisTemplate.expire(sKey, STATS_TTL);

        // 注：version 不在写路径递增，由 MongoDB @Version 在 consumer 写回时推进，
        // consumer 成功后通过 deleteIfCold 让 Redis 重新 read-through 加载新 version。

        return true;
    }

    @Transactional
    public Boolean cancelLikeRemark(String remarkId, Long userId) {
        // 1. 写路径 read-through：确保 Redis 反映了 MongoDB 中的真实点赞状态
        initStatsIfNeeded(remarkId);

        String uKey = userLikeKey(remarkId);
        if (!Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(uKey, userId))) {
            return false;
        }

        redisTemplate.opsForSet().remove(uKey, userId);
        redisTemplate.expire(uKey, STATS_TTL);

        // 2. HINCRBY count -1，下溢则复位 0
        HashOperations<String, Object, Object> hops = redisTemplate.opsForHash();
        String sKey = statsKey(remarkId);
        Long newCount = hops.increment(sKey, F_COUNT, -1L);
        if (newCount != null && newCount < 0) {
            hops.put(sKey, F_COUNT, "0");
        }
        hops.put(sKey, F_LAST_ACTIVITY, LocalDateTime.now().toString());
        redisTemplate.expire(sKey, STATS_TTL);

        return true;
    }

    /**
     * 把 Redis 中所有 stats Hash 推送到 MQ：count + version + last_activity_at。
     * 学 NoteStatsService.flushToMQ；消费者用 version 做乐观锁，用 last_activity_at 决定是否 deleteIfCold。
     */
    public void flushLikeCountToMQ() {
        Set<String> keys = redisTemplate.keys(STATS_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                String remarkId = key.substring(STATS_KEY_PREFIX.length());
                if (remarkId.isEmpty()) continue;

                Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
                if (hash.isEmpty()) continue;

                Map<String, Object> msg = new HashMap<>();
                msg.put("remarkId", remarkId);
                msg.put("likeCount", Math.max(0L, parseLongSafe(hash.get(F_COUNT))));
                msg.put("version", parseLongSafe(hash.get(F_VERSION)));
                msg.put("last_activity_at",
                        hash.getOrDefault(F_LAST_ACTIVITY, LocalDateTime.now().toString()));

                rabbitTemplate.convertAndSend(LikeCountQueue, msg);
            } catch (Exception e) {
                log.error("flushLikeCountToMQ error for key={}", key, e);
            }
        }
    }

    public void flushLikeUsersToMQ() {
        Set<String> keys = redisTemplate.keys(USER_LIKE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                String remarkId = key.substring(USER_LIKE_KEY_PREFIX.length());
                if (remarkId.isEmpty()) continue;

                Set<Object> redisSet = redisTemplate.opsForSet().members(key);
                Set<Long> userSet = new HashSet<>();
                if (redisSet != null && !redisSet.isEmpty()) {
                    userSet = redisSet.stream()
                            .map(val -> {
                                try { return Long.parseLong(val.toString()); }
                                catch (NumberFormatException e) { return null; }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                }

                // version + last_activity_at 与 count 共用 stats Hash，作为单一事实源
                Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey(remarkId));
                long version = parseLongSafe(stats.get(F_VERSION));
                Object lastAct = stats.getOrDefault(F_LAST_ACTIVITY, LocalDateTime.now().toString());

                Map<String, Object> msg = new HashMap<>();
                msg.put("remarkId", remarkId);
                msg.put("users", userSet);
                msg.put("version", version);
                msg.put("last_activity_at", lastAct);

                rabbitTemplate.convertAndSend(LikeUsersQueue, msg);
            } catch (Exception e) {
                log.error("flushLikeUsersToMQ error for key={}", key, e);
            }
        }
    }
// --- 统计和列表查询 ---

    @Transactional
    public Long getRemarkCount() {
        return remarkRepository.count();
    }

    @Transactional
    public List<RemarkDetailVO> getAllRemarks() {
        List<RemarkDO> remarkDOList = remarkRepository.findAll(Sort.by(Sort.Direction.ASC, "_id"));
        
        if (remarkDOList == null || remarkDOList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> noteIds = remarkDOList.stream()
                .map(RemarkDO::getNoteId)
                .filter(noteId -> noteId != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> noteTitleMap = new HashMap<>();
        for (Long noteId : noteIds) {
            try {
                NoteDO note = noteMapper.selectById(noteId);
                if (note != null) {
                    noteTitleMap.put(noteId, note.getTitle());
                } else {
                    noteTitleMap.put(noteId, "笔记已删除");
                }
            } catch (Exception e) {
                log.warn("获取笔记标题失败, noteId={}", noteId, e);
                noteTitleMap.put(noteId, "未知笔记");
            }
        }

        List<RemarkDetailVO> voList = new ArrayList<>();
        for (RemarkDO remarkDO : remarkDOList) {
            RemarkDetailVO vo = RemarkDetailVO.builder()
                    ._id(remarkDO.get_id())
                    .noteId(remarkDO.getNoteId())
                    .noteTitle(noteTitleMap.getOrDefault(remarkDO.getNoteId(), "未知笔记"))
                    .userId(remarkDO.getUserId())
                    .username(remarkDO.getUsername())
                    .content(remarkDO.getContent())
                    .createdAt(remarkDO.getCreatedAt())
                    .parentId(remarkDO.getParentId())
                    .replyToUsername(remarkDO.getReplyToUsername())
                    .isReply(remarkDO.getIsReply())
                    .replyToRemarkId(remarkDO.getReplyToRemarkId())
                    .build();
            voList.add(vo);
        }

        return voList;
    }

    @Transactional //通过某一节点查询构建评论树
    public RemarkVO getRemarkTreeByRemarkId(String remarkId, Long loginUserId) {
        if (remarkId == null || remarkId.isEmpty()) {
            throw new RuntimeException("评论ID不能为空");
        }

        RemarkDO targetRemark = remarkRepository.findByRemarkId(remarkId)
                .orElseThrow(() -> new RuntimeException("评论不存在"));

        RemarkDO firstLevelRemark = findFirstLevelRemark(targetRemark);

        UserDO user = userMapper.selectById(loginUserId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        List<RemarkDO> secondLevelRemarks = remarkRepository.findRemarksByParentIdAndIsReplyTrue(firstLevelRemark.get_id());

        Comparator<RemarkDO> timeComparator = (r1, r2) -> {
            String time1 = r1.getCreatedAt() != null ? r1.getCreatedAt() : "";
            String time2 = r2.getCreatedAt() != null ? r2.getCreatedAt() : "";
            return time1.compareTo(time2);
        };
        secondLevelRemarks.sort(timeComparator);

        List<RemarkVO> secondLevelVOList = new ArrayList<>();
        for (RemarkDO secondLevel : secondLevelRemarks) {
            RemarkVO secondLevelVO = transferDO2VO(secondLevel, user);
            
            List<RemarkDO> thirdLevelRemarks = remarkRepository.findRemarksByParentIdAndIsReplyTrue(secondLevel.get_id());
            thirdLevelRemarks.sort(timeComparator);
            
            List<RemarkVO> thirdLevelVOList = new ArrayList<>();
            for (RemarkDO thirdLevel : thirdLevelRemarks) {
                RemarkVO thirdLevelVO = transferDO2VO(thirdLevel, user);
                thirdLevelVOList.add(thirdLevelVO);
            }
            
            secondLevelVO.setReplies(thirdLevelVOList);
            secondLevelVOList.add(secondLevelVO);
        }

        RemarkVO firstLevelVO = transferDO2VO(firstLevelRemark, user);
        firstLevelVO.setReplies(secondLevelVOList);

        return firstLevelVO;
    }

    private RemarkDO findFirstLevelRemark(RemarkDO remark) {

        if (Boolean.FALSE.equals(remark.getIsReply()) || remark.getParentId() == null) {
            return remark;
        }

        Optional<RemarkDO> parent = remarkRepository.findByRemarkId(remark.getParentId());
        if (parent.isEmpty()) {
 
            log.warn("找不到父评论，parentId: {}", remark.getParentId());
            return remark;
        }

        return findFirstLevelRemark(parent.get());
    }

}
