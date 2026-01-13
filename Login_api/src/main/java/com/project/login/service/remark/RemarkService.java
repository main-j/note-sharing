package com.project.login.service.remark;


import com.project.login.convert.RemarkConvert;
import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.RemarkCountDO;
import com.project.login.model.dataobject.RemarkDO;
import com.project.login.model.dataobject.RemarkLikeByUsersDO;
import com.project.login.model.dataobject.UserDO;
import com.project.login.model.dto.remark.RemarkDeleteDTO;
import com.project.login.model.dto.remark.RemarkInsertDTO;
import com.project.login.model.dto.remark.RemarkSelectByNoteDTO;
import com.project.login.model.vo.RemarkVO;
import com.project.login.model.vo.RemarkDetailVO;
import com.project.login.model.dataobject.NoteDO;
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
    private final String remarkLikeCountKey = "remark_like_count:";
    private final String remarkIdKey = "remark:";
    private final String NoteIdKey = "note_id_of_remark_list:";
    private final String replyToIdKey = "reply_to:";
    private final String userLikeRemarkListKey = "remark_user_like:";
    private final RabbitTemplate rabbitTemplate;
    private final String LikeCountQueue="remarkLikeCount.redis.queue";
    private final String LikeUsersQueue="remarkLikeUsers.redis.queue";
    private final NotificationService notificationService;

    private RemarkVO transferDO2VO(RemarkDO remarkDO, UserDO user) {
        //初始转化与变量准备
        RemarkVO cur = remarkConvert.toVO(remarkDO);
        Long loginUserId = user.getId();

        // ------ 设置是否已点赞 ------
        String redisKey = userLikeRemarkListKey + remarkDO.get_id();
        boolean liked = false;

        if (redisTemplate.hasKey(redisKey)) {
            // Redis 有数据 → 直接判断
            liked = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(redisKey, loginUserId));
            redisTemplate.expire(redisKey,Duration.ofMinutes(15));

        } else {
            // Redis 无数据 → 从数据库加载
            Optional<RemarkLikeByUsersDO> likeRecord = remarkLikeByUsersRepository.findById(remarkDO.get_id());

            if (likeRecord.isPresent()) {
                Set<Long> userList = likeRecord.get().getUserList();

                if (userList != null && !userList.isEmpty()) {
                    // 判断是否点赞
                    liked = userList.contains(loginUserId);

                    // 同步整个列表到 Redis
                    redisTemplate.opsForSet().add(redisKey, userList.toArray());
                    redisTemplate.expire(redisKey,Duration.ofMinutes(15));
                }
            }
        }
        // ------ 加载点赞数量 ------
        Long likedCount = 0L;
        String countKey = remarkLikeCountKey + remarkDO.get_id();

        if (redisTemplate.hasKey(countKey)) {

            Object countObj = redisTemplate.opsForValue().get(countKey);
            redisTemplate.expire(countKey,Duration.ofMinutes(15));
            if (countObj instanceof Long l) {
                likedCount = l;
            } else if (countObj instanceof Integer i) {
                likedCount = i.longValue();
            }

        } else {

            // Redis 无计数 → 从数据库加载
            RemarkCountDO remarkCount = remarkLikeCountRepository.findById(remarkDO.get_id())
                    .orElse(null);

            if (remarkCount != null) {
                likedCount = remarkCount.getRemarkLikeCount();
                // 同步到 Redis（hash）
                redisTemplate.opsForValue().set(countKey, remarkCount.getRemarkLikeCount());
                redisTemplate.expire(countKey,Duration.ofMinutes(15));
            }
        }
        log.info("successfully toVO");
        cur.setLikedOrNot(liked);
        cur.setLikeCount(likedCount);

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

            // 先查 Redis
            RemarkDO childDO = null;
            String redisKey = remarkIdKey + child.get_id();
            if (redisTemplate.hasKey(redisKey)) {
                childDO = (RemarkDO) redisTemplate.opsForValue().get(redisKey);
                redisTemplate.expire(redisKey, Duration.ofMinutes(15));
            } else {
                // Redis 没有则从 MongoDB 获取
                childDO = remarkRepository.findById(child.get_id()).orElse(null);
                if (childDO != null) {
                    redisTemplate.opsForValue().set(redisKey, childDO);
                    redisTemplate.expire(redisKey, Duration.ofMinutes(15));
                }
            }

            if (childDO == null) continue;

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
        List<String> remarkFirstLayerList = new ArrayList<>();
        List<RemarkVO> result = new ArrayList<>();

// --- 一级评论列表处理 ---
        log.info("initialize complete");
        if (redisTemplate.hasKey(noteKey)) {
            // 从Redis读取一级评论ID列表
            List<Object> tmpList = redisTemplate.opsForList().range(noteKey, 0, -1);
            log.info("found in redis"+tmpList.toString());
            redisTemplate.expire(noteKey,Duration.ofMinutes(15));
            if (tmpList != null) {
                remarkFirstLayerList = tmpList.stream().map(Object::toString).toList();
                log.info(remarkFirstLayerList.toString());
            }
        } else {
            // Redis中没有则从MongoDB读取
            log.info("finding firstLayer in mongodb");
            List<RemarkDO> tmpRemarkDOList = remarkRepository.findRemarksByNoteIdAndIsReplyFalse(noteId);
            log.info("get from mongodb,tmpRemarkDOList= "+tmpRemarkDOList.toString());
            for (RemarkDO cur : tmpRemarkDOList) {
                if (cur != null && cur.get_id() != null) {
                    remarkFirstLayerList.add(cur.get_id());
                }
            }
            // 将一级评论ID列表写入Redis
            if (!remarkFirstLayerList.isEmpty()) {
                remarkFirstLayerList.forEach(id -> redisTemplate.opsForList().rightPush(noteKey, id));
                redisTemplate.expire(noteKey,Duration.ofMinutes(15));
            }
            log.info("found the firstLayer");
        }

// --- 遍历一级评论 ---
        for (String cur : remarkFirstLayerList) {
            RemarkDO curDO = null;
            // 尝试从Redis获取一级评论对象
            if (redisTemplate.hasKey(remarkIdKey + cur)) {
                curDO = (RemarkDO) redisTemplate.opsForValue().get(remarkIdKey + cur);
                redisTemplate.expire(remarkIdKey,Duration.ofMinutes(15));
            }
            else {
                // Redis没有则从MongoDB获取
                curDO = remarkRepository.findById(cur).orElse(null);
                if (curDO != null) {
                    redisTemplate.opsForValue().set(remarkIdKey + cur, curDO);
                    redisTemplate.expire(remarkIdKey,Duration.ofMinutes(15));
                }
            }

            // 如果一级评论对象为空，跳过
            if (curDO == null) continue;
            log.info(curDO.toString());
            // 将DO转换为VO
            RemarkVO curVO = transferDO2VO(curDO, user);
            log.info(curVO.toString());
            // --- 构建无限层级回复树 ---
            List<RemarkVO> replies = buildReplyTree(curDO.get_id(), user);
            curVO.setReplies(replies);
            result.add(curVO);
            log.info("show curVO"+curVO.toString());
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
                    remarkSecondLayerList = tmpReplyList.stream().map(Object::toString).toList();
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
                    replyDO = remarkRepository.findById(replyId).orElse(null);
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
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert remark", e);
        }
    }

    @Transactional
    public Boolean deleteRemark(RemarkDeleteDTO remarkDeleteDTO) {
        try {
            // 1. 查找要删除的评论
            RemarkDO remarkDO = remarkRepository.findById(remarkDeleteDTO.getId())
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
                        childIds = tmpList.stream().map(Object::toString).toList();
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
                    redisTemplate.delete(remarkLikeCountKey + childId);
                    redisTemplate.delete(userLikeRemarkListKey + childId);
                    redisTemplate.delete(remarkIdKey + childId);
                    noteStatsService.changeField(noteId,"comments",-1);
                }

                redisTemplate.delete(replyKey);
                remarkRepository.deleteByParentId(parentId);
            }

            // 3. 删除该评论的点赞记录和数据库记录
            remarkLikeByUsersRepository.deleteById(remarkDO.get_id());
            remarkRepository.deleteById(remarkDO.get_id());
            redisTemplate.delete(remarkLikeCountKey+remarkDO.get_id());
            redisTemplate.delete(userLikeRemarkListKey+remarkDO.get_id());
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


    @Transactional
    public Boolean likeRemark(String remarkId, Long userId) {

        String redisKey = userLikeRemarkListKey + remarkId;
        log.info("starting like");
        // 1. 检查用户是否已点赞
        Boolean exists = false;
        String userStr=userId.toString();
        if (redisTemplate.hasKey(redisKey)) {
            log.info("try to get from redis");
            exists = redisTemplate.opsForSet().isMember(redisKey, userStr);
            redisTemplate.expire(redisKey,Duration.ofMinutes(15));
            log.info("get from redis complete");
        } else {
            // Redis 无数据，则从数据库加载
            log.info("try to get from db");
            Optional<RemarkLikeByUsersDO> likeRecord =
                    remarkLikeByUsersRepository.findById(remarkId);

            if (likeRecord.isPresent()) {

                Set<Long> userList = likeRecord.get().getUserList();

                if (userList != null && !userList.isEmpty()) {
                    exists = userList.contains(userId);

                    // 同步整个 Set 到 Redis
                    List<Long> tmpList=userList.stream().toList();
                    tmpList.forEach(user->redisTemplate.opsForSet().add(redisKey,user));
                    redisTemplate.expire(redisKey,Duration.ofMinutes(15));
                } else {
                    exists = false;
                }
            } else {
                exists = false;
            }
        }

        if (Boolean.TRUE.equals(exists)) {
            return false; // 已经点赞过
        }

        // 3. 更新 Redis 缓存
        redisTemplate.opsForSet().add(redisKey, userId);

        // 4. 更新评论的 likes 数
        String countKey = remarkLikeCountKey + remarkId;

        Long newCount;

        if (redisTemplate.hasKey(countKey)) {
            log.info("getting count from redis");
            // Redis 已有点赞计数，直接自增
            newCount = redisTemplate.opsForValue().increment(countKey, 1L);
            redisTemplate.expire(countKey,Duration.ofMinutes(15));
            log.info(newCount.toString());

        } else {
            log.info("getting count from db");
            // Redis 中没有，从 MongoDB 加载
            RemarkCountDO remarkCount = remarkLikeCountRepository.findById(remarkId)
                    .orElseGet(() -> RemarkCountDO.builder()
                            .remarkId(remarkId)
                            .remarkLikeCount(0L)
                            .build()
                    );

            // 同步到 Redis（作为 Hash）
            redisTemplate.opsForValue().set(countKey,remarkCount.getRemarkLikeCount());
            redisTemplate.expire(countKey,Duration.ofMinutes(15));

            // 自增一次
            redisTemplate.opsForValue().increment(countKey, 1L);
        }


        return true;
    }

    @Transactional
    public Boolean cancelLikeRemark(String remarkId, Long userId) {

        String redisKey = userLikeRemarkListKey + remarkId;

        // 1. 判断是否已点赞（Redis → MongoDB）
        Boolean exists = false;
        if (redisTemplate.hasKey(redisKey)) {
            exists = redisTemplate.opsForSet().isMember(redisKey, userId);
            redisTemplate.expire(redisKey,Duration.ofMinutes(15));
        } else {
            // Redis 无数据 → MongoDB 加载
            Optional<RemarkLikeByUsersDO> likeRecord =
                    remarkLikeByUsersRepository.findById(remarkId);

            if (likeRecord.isPresent()) {
                Set<Long> userList = likeRecord.get().getUserList();

                if (userList != null && !userList.isEmpty()) {
                    exists = userList.contains(userId);

                    // 同步整个 Set 到 Redis
                    List<Long> tmpList=userList.stream().toList();
                    tmpList.forEach(user->redisTemplate.opsForSet().add(redisKey,user));
                    redisTemplate.expire(redisKey,Duration.ofMinutes(15));
                } else {
                    exists = false;
                }
            } else {
                exists = false;
            }
        }

        if (!Boolean.TRUE.equals(exists)) {
            return false; // 根本没点赞过
        }


        // 2. Redis 取消点赞
        redisTemplate.opsForSet().remove(redisKey, userId);


        // 3. MongoDB 中从 userList 移除用户
        remarkLikeByUsersRepository.findById(remarkId).ifPresent(record -> {
            Set<Long> userList = record.getUserList();
            if (userList != null) {
                userList.remove(userId);
            }
            remarkLikeByUsersRepository.save(record);
        });


        // 4. 更新点赞计数（Redis → MongoDB）
        String countKey = remarkLikeCountKey + remarkId;

        if (redisTemplate.hasKey(countKey)) {

            // Redis 已有计数，直接减 1（但不低于 0）
            Long current = redisTemplate.opsForValue().increment(countKey,-1L);
            redisTemplate.expire(countKey,Duration.ofMinutes(15));
            if (current == null || current <0) {
                redisTemplate.opsForValue().set(countKey, 0L);
                return false;
            }
        } else {
            // Redis 无计数 → 从 DB 加载
            RemarkCountDO remarkCount = remarkLikeCountRepository.findById(remarkId).orElse(null);
            if (remarkCount == null) {
                return false;
            }


            // 同步到 Redis
            redisTemplate.opsForValue().set(countKey, remarkCount.getRemarkLikeCount());
            redisTemplate.expire(redisKey,Duration.ofMinutes(15));

            // 自减一次（不低于 0）
            if (remarkCount.getRemarkLikeCount() > 0) {
                redisTemplate.opsForValue().increment(countKey, -1);
            }
        }
        return true;
    }

    public void flushLikeCountToMQ(){
        Set<String> keys = redisTemplate.keys(remarkLikeCountKey + "*");
        if (keys.isEmpty()) return;

        keys.forEach(key -> {
                String remarkId=key.split(":")[1];
                if(remarkId==null){
                    return;
                }
                Long likeCount=redisTemplate.opsForValue().increment(key,0L);
                if(likeCount!=null){
                    log.info(likeCount.toString());
                }
                if (likeCount==null) return;
                Map<String, Object> msg = new HashMap<>();
                msg.put("remarkId",remarkId);
                msg.put("likeCount",likeCount);
                rabbitTemplate.convertAndSend(LikeCountQueue, msg);
        });
    }
    public void flushLikeUsersToMQ() {
        // 获取所有点赞用户相关的 Redis key
        Set<String> keys = redisTemplate.keys(userLikeRemarkListKey + "*"); // 假设前缀是 remarkUserLikeKey
        if (keys.isEmpty()) return;

        for (String key : keys) {
            try {
                // 1. 解析 remarkId（key 格式：remark_user_like:{remarkId}）
                String[] parts = key.split(":");
                if (parts.length < 2) {
                    continue; // key 格式不正确时跳过
                }
                String remarkId = parts[1];

                // 2. 获取 Redis Set 中所有用户
                Set<Object> redisSet = redisTemplate.opsForSet().members(key);
                Set<Long> userSet = new HashSet<>();
                if (!redisSet.isEmpty()) {
                    userSet = redisSet.stream()
                            .map(val -> {
                                try {
                                    return Long.parseLong(val.toString());
                                } catch (NumberFormatException e) {
                                    return null; // 转换失败的值忽略
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                }
                // 3. 构造发送消息
                Map<String, Object> msg = new HashMap<>();
                msg.put("remarkId", remarkId);
                msg.put("users", userSet);

                // 4. 发送到 RabbitMQ
                rabbitTemplate.convertAndSend(LikeUsersQueue, msg);

            } catch (Exception e) {
                log.error("fail to send");
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

        RemarkDO targetRemark = remarkRepository.findById(remarkId)
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

        Optional<RemarkDO> parent = remarkRepository.findById(remark.getParentId());
        if (parent.isEmpty()) {
 
            log.warn("找不到父评论，parentId: {}", remark.getParentId());
            return remark;
        }

        return findFirstLevelRemark(parent.get());
    }

}
