package com.project.login.service.rabbitmq.consumer;

import com.project.login.model.dataobject.RemarkCountDO;
import com.project.login.model.dataobject.RemarkLikeByUsersDO;
import com.project.login.repository.RemarkLikeByUsersRepository;
import com.project.login.repository.RemarkLikeCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Remark 点赞数据 Redis → MongoDB 持久化消费者。
 * <p>
 * 设计参考 {@code NoteStatsConsumer}（去掉补偿表机制）：
 * <ul>
 *   <li>Layer 1：直接覆盖（version 一致时全量写入）</li>
 *   <li>Layer 2：乐观锁冲突时 fallback 到 delta 增量（带重试）</li>
 *   <li>deleteIfCold：成功落盘后，若 Redis 仍是同一份快照（last_activity_at 未前进），
 *       主动删除 Redis 中的 stats / userList key，强制下一次访问 read-through 从 DB
 *       重新加载新 version。这是把 Redis 当"热缓存"而非"权威源"的关键。</li>
 *   <li>失败仅记录日志，不写补偿表（与 NoteStats 的区别）。下一次定时 flush 会重发。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemarkConsumer {

    private final RemarkLikeCountRepository remarkLikeCountRepository;
    private final RemarkLikeByUsersRepository remarkLikeByUsersRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATS_KEY_PREFIX = "remark_stats:";
    private static final String USER_LIKE_KEY_PREFIX = "remark_user_like:";
    private static final String F_LAST_ACTIVITY = "last_activity_at";

    /** 消息: { remarkId, likeCount, version, last_activity_at } */
    @RabbitListener(queues = "remarkLikeCount.redis.queue")
    public void handleLikeCountMessage(Map<String, Object> msg) {
        String remarkId = (String) msg.get("remarkId");
        if (remarkId == null || remarkId.isEmpty()) {
            log.warn("handleLikeCountMessage: empty remarkId, skip");
            return;
        }

        long likeCount = Math.max(0L, parseLongSafe(msg.get("likeCount")));
        long version = parseLongSafe(msg.get("version"));
        LocalDateTime lastActivity = parseDateTimeSafe(msg.get("last_activity_at"));

        // Layer 1: direct write with version check
        try {
            if (directUpdateCount(remarkId, likeCount, version)) {
                deleteStatsIfCold(remarkId, lastActivity);
                return;
            }
        } catch (OptimisticLockingFailureException e) {
            log.info("Optimistic lock conflict for count remarkId={}, falling back to delta", remarkId);
        }

        // Layer 2: delta update with retry
        int maxRetry = 3;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                deltaUpdateCount(remarkId, likeCount);
                deleteStatsIfCold(remarkId, lastActivity);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == maxRetry) {
                    log.error("Delta count update failed for remarkId={} after {} attempts (giving up; "
                            + "next flush will retry)", remarkId, maxRetry);
                } else {
                    log.info("Delta count update conflict for remarkId={}, retry {}/{}", remarkId, attempt, maxRetry);
                }
            } catch (Exception e) {
                log.error("Error in delta count update for remarkId={}", remarkId, e);
                return;
            }
        }
    }

    /** 消息: { remarkId, users, version, last_activity_at } */
    @RabbitListener(queues = "remarkLikeUsers.redis.queue")
    public void handleLikeUsersMessage(Map<String, Object> msg) {
        String remarkId = (String) msg.get("remarkId");
        if (remarkId == null || remarkId.isEmpty()) {
            log.warn("handleLikeUsersMessage: empty remarkId, skip");
            return;
        }

        Set<Long> userSet = parseUserSet(msg.get("users"));
        long version = parseLongSafe(msg.get("version"));
        LocalDateTime lastActivity = parseDateTimeSafe(msg.get("last_activity_at"));

        // Layer 1: direct overwrite with version check
        try {
            if (directUpdateUsers(remarkId, userSet, version)) {
                deleteUsersIfCold(remarkId, lastActivity);
                return;
            }
        } catch (OptimisticLockingFailureException e) {
            log.info("Optimistic lock conflict for users remarkId={}, falling back to retry-overwrite", remarkId);
        }

        // Layer 2: 集合无 delta 概念 → 重新拉取 + 覆盖，最多 3 次重试
        int maxRetry = 3;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                forceOverwriteUsers(remarkId, userSet);
                deleteUsersIfCold(remarkId, lastActivity);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == maxRetry) {
                    log.error("Users overwrite failed for remarkId={} after {} attempts (giving up; "
                            + "next flush will retry)", remarkId, maxRetry);
                } else {
                    log.info("Users overwrite conflict for remarkId={}, retry {}/{}", remarkId, attempt, maxRetry);
                }
            } catch (Exception e) {
                log.error("Error in users overwrite for remarkId={}", remarkId, e);
                return;
            }
        }
    }

    // ============ Count helpers ============

    private boolean directUpdateCount(String remarkId, long likeCount, long msgVersion) {
        RemarkCountDO existing = remarkLikeCountRepository.findById(remarkId).orElse(null);
        long dbVersion = (existing != null && existing.getVersion() != null) ? existing.getVersion() : 0L;

        if (existing != null && dbVersion != msgVersion) {
            return false;
        }

        RemarkCountDO countDO = existing != null ? existing
                : RemarkCountDO.builder().remarkId(remarkId).build();
        countDO.setRemarkLikeCount(likeCount);
        // @Version 字段由 Spring Data 自动 +1 维护，这里不要手动赋 msgVersion，
        // 否则会把 DB 已经推进的版本号覆盖回去（对新插入则由 Spring 设为 0）。
        remarkLikeCountRepository.save(countDO);
        return true;
    }

    private void deltaUpdateCount(String remarkId, long likeCount) {
        RemarkCountDO dbCount = remarkLikeCountRepository.findById(remarkId).orElse(null);
        long dbLikeCount = (dbCount != null && dbCount.getRemarkLikeCount() != null)
                ? dbCount.getRemarkLikeCount() : 0L;

        long delta = likeCount - dbLikeCount;
        RemarkCountDO countDO = dbCount != null ? dbCount
                : RemarkCountDO.builder().remarkId(remarkId).remarkLikeCount(0L).build();
        countDO.setRemarkLikeCount(Math.max(0L, dbLikeCount + delta));
        remarkLikeCountRepository.save(countDO);
    }

    // ============ Users helpers ============

    private boolean directUpdateUsers(String remarkId, Set<Long> userSet, long msgVersion) {
        RemarkLikeByUsersDO existing = remarkLikeByUsersRepository.findById(remarkId).orElse(null);
        long dbVersion = (existing != null && existing.getVersion() != null) ? existing.getVersion() : 0L;

        if (existing != null && dbVersion != msgVersion) {
            return false;
        }

        RemarkLikeByUsersDO usersDO = existing != null ? existing
                : RemarkLikeByUsersDO.builder().remarkId(remarkId).userList(new HashSet<>()).build();
        usersDO.setUserList(userSet);
        remarkLikeByUsersRepository.save(usersDO);
        return true;
    }

    private void forceOverwriteUsers(String remarkId, Set<Long> userSet) {
        RemarkLikeByUsersDO existing = remarkLikeByUsersRepository.findById(remarkId).orElse(null);
        RemarkLikeByUsersDO usersDO = existing != null ? existing
                : RemarkLikeByUsersDO.builder().remarkId(remarkId).userList(new HashSet<>()).build();
        usersDO.setUserList(userSet);
        remarkLikeByUsersRepository.save(usersDO);
    }

    // ============ deleteIfCold ============

    /**
     * 学 NoteStatsConsumer.deleteIfCold：消息处理成功后，若 Redis 中的 last_activity_at
     * 没有比消息携带的 last_activity_at 更新（说明这条消息发出后没有新的写入），
     * 就把 Redis stats Hash 删除。下一次 like/cancel/读 会触发 read-through 重新加载，
     * 让 Redis 看到 MongoDB 中最新的 version。
     * <p>
     * 如果 Redis 中 last_activity_at 已经更晚（有新写入），就保留 Redis 数据，下一轮 flush 再同步。
     */
    private void deleteStatsIfCold(String remarkId, LocalDateTime incomingLast) {
        String key = STATS_KEY_PREFIX + remarkId;
        Object redisLastObj = redisTemplate.opsForHash().get(key, F_LAST_ACTIVITY);

        if (redisLastObj == null) {
            redisTemplate.delete(key);
            return;
        }

        try {
            LocalDateTime redisLast = LocalDateTime.parse(redisLastObj.toString());
            if (!redisLast.isAfter(incomingLast)) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            redisTemplate.delete(key);
        }
    }

    /**
     * userList Set 没有自带 last_activity_at，复用 stats Hash 的时间戳：
     * 如果 stats Hash 已被 deleteStatsIfCold 删除（或者 stats 时间戳没领先），就一并清掉用户集合。
     */
    private void deleteUsersIfCold(String remarkId, LocalDateTime incomingLast) {
        String statsK = STATS_KEY_PREFIX + remarkId;
        String userK = USER_LIKE_KEY_PREFIX + remarkId;
        Object redisLastObj = redisTemplate.opsForHash().get(statsK, F_LAST_ACTIVITY);

        if (redisLastObj == null) {
            redisTemplate.delete(userK);
            return;
        }

        try {
            LocalDateTime redisLast = LocalDateTime.parse(redisLastObj.toString());
            if (!redisLast.isAfter(incomingLast)) {
                redisTemplate.delete(userK);
            }
        } catch (Exception e) {
            redisTemplate.delete(userK);
        }
    }

    // ============ Utils ============

    private long parseLongSafe(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        if (obj == null) return 0L;
        try { return Long.parseLong(obj.toString()); }
        catch (NumberFormatException e) { return 0L; }
    }

    private LocalDateTime parseDateTimeSafe(Object obj) {
        if (obj == null) return LocalDateTime.now();
        try { return LocalDateTime.parse(obj.toString()); }
        catch (Exception e) { return LocalDateTime.now(); }
    }

    private Set<Long> parseUserSet(Object obj) {
        Set<Long> result = new HashSet<>();
        if (obj instanceof Iterable) {
            for (Object o : (Iterable<?>) obj) {
                if (o != null) {
                    try { result.add(Long.parseLong(o.toString())); }
                    catch (NumberFormatException ignored) {
                        // skip non-numeric ids
                    }
                }
            }
        }
        return result;
    }
}
