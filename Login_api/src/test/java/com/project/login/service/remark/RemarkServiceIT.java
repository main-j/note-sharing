package com.project.login.service.remark;

import com.project.login.model.dataobject.RemarkCountDO;
import com.project.login.repository.RemarkLikeByUsersRepository;
import com.project.login.repository.RemarkLikeCountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * RemarkService 集成测试（按 notestat 模式重写）：
 *   - 真实 Redis + 真实 MongoDB；RabbitTemplate 用 @MockitoBean 拦截
 *   - Redis 新 schema：remark_stats:{id} (Hash) + remark_user_like:{id} (Set)
 *   - @BeforeEach / @AfterEach 同时清理 Redis 与 MongoDB，避免状态污染
 *   - 校验：
 *       * read-through 在写路径生效（DB 已有计数时不被腰斩）
 *       * version 不再由 like/cancel 维护，由 DB 加载
 *       * last_activity_at 在每次写操作中被更新
 *       * 长 TTL（7 天）远大于 flush 周期
 */
@SpringBootTest
class RemarkServiceIT {

    private static final Long UA = 1001L;
    private static final Long UB = 1002L;
    /** TTL 上限（秒）—— 与 RemarkService.STATS_TTL = Duration.ofHours(2) 对齐 */
    private static final long TTL_UPPER_BOUND_SECONDS = 2L * 60L * 60L;

    @Autowired private RemarkService remarkService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private RemarkLikeCountRepository countRepo;
    @Autowired private RemarkLikeByUsersRepository usersRepo;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    private String id;
    private String statsKey;
    private String userLikeKey;

    @BeforeEach
    void cleanState() {
        id = "it-remark-" + UUID.randomUUID();
        statsKey = "remark_stats:" + id;
        userLikeKey = "remark_user_like:" + id;
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        redisTemplate.delete(statsKey);
        redisTemplate.delete(userLikeKey);
        countRepo.deleteById(id);
        usersRepo.deleteById(id);
    }

    private long getCount() {
        Object v = redisTemplate.opsForHash().get(statsKey, "count");
        return v == null ? 0L : Long.parseLong(v.toString());
    }

    private long getVersion() {
        Object v = redisTemplate.opsForHash().get(statsKey, "version");
        return v == null ? 0L : Long.parseLong(v.toString());
    }

    private String getLastActivity() {
        Object v = redisTemplate.opsForHash().get(statsKey, "last_activity_at");
        return v == null ? null : v.toString();
    }

    private long ttl(String key) {
        Long t = redisTemplate.getExpire(key);
        return t == null ? -1L : t;
    }

    // ============ likeRemark ============

    @Test
    void likeRemark_firstTime_persistsStatsAndUserWithLongTTL() {
        assertThat(remarkService.likeRemark(id, UA)).isTrue();

        assertThat(getCount()).isEqualTo(1L);
        assertThat(getVersion()).isZero();              // 不再每次 like +1
        assertThat(getLastActivity()).isNotNull();
        assertThat(redisTemplate.opsForSet().isMember(userLikeKey, UA)).isTrue();
        assertThat(redisTemplate.opsForSet().size(userLikeKey)).isEqualTo(1L);

        // TTL 应在 (0, 7 天] 区间
        assertThat(ttl(statsKey)).isBetween(1L, TTL_UPPER_BOUND_SECONDS);
        assertThat(ttl(userLikeKey)).isBetween(1L, TTL_UPPER_BOUND_SECONDS);
    }

    @Test
    void likeRemark_duplicate_isRejected() {
        assertThat(remarkService.likeRemark(id, UA)).isTrue();
        assertThat(remarkService.likeRemark(id, UA)).isFalse();

        assertThat(getCount()).isEqualTo(1L);
        assertThat(redisTemplate.opsForSet().size(userLikeKey)).isEqualTo(1L);
    }

    @Test
    void likeRemark_multipleUsers_setSizeAndCountMatch() {
        remarkService.likeRemark(id, UA);
        remarkService.likeRemark(id, UB);

        assertThat(getCount()).isEqualTo(2L);
        assertThat(redisTemplate.opsForSet().size(userLikeKey)).isEqualTo(2L);
        assertThat(redisTemplate.opsForSet().isMember(userLikeKey, UA)).isTrue();
        assertThat(redisTemplate.opsForSet().isMember(userLikeKey, UB)).isTrue();
    }

    /**
     * 关键场景：MongoDB 已有点赞记录但 Redis 缓存缺失时，写路径 read-through 应从 DB 加载而非归零。
     * 这是修复"缓存过期 → 计数腰斩"的核心保护。
     */
    @Test
    void likeRemark_redisMissAndDbHasExistingCount_readsThroughInsteadOfResetting() {
        // 模拟 MongoDB 已有 100 个点赞，但 Redis 缓存已过期
        countRepo.save(RemarkCountDO.builder().remarkId(id).remarkLikeCount(100L).build());

        assertThat(remarkService.likeRemark(id, UA)).isTrue();

        // count 应该是 101 (100 + 1)，而非 1（如果不 read-through 就会是 1）
        assertThat(getCount()).isEqualTo(101L);
    }

    // ============ cancelLikeRemark ============

    @Test
    void cancelLikeRemark_existingLike_decrementsCountWithoutTouchingVersion() {
        remarkService.likeRemark(id, UA);
        long versionBefore = getVersion();

        assertThat(remarkService.cancelLikeRemark(id, UA)).isTrue();
        assertThat(getCount()).isZero();
        assertThat(getVersion()).isEqualTo(versionBefore);   // version 不变
        assertThat(redisTemplate.opsForSet().isMember(userLikeKey, UA)).isFalse();
    }

    @Test
    void cancelLikeRemark_withoutPriorLike_isRejected() {
        assertThat(remarkService.cancelLikeRemark(id, UA)).isFalse();
    }

    @Test
    void cancelLikeRemark_oneOfTwoUsers_leavesOtherIntact() {
        remarkService.likeRemark(id, UA);
        remarkService.likeRemark(id, UB);

        assertThat(remarkService.cancelLikeRemark(id, UA)).isTrue();

        assertThat(getCount()).isEqualTo(1L);
        assertThat(redisTemplate.opsForSet().isMember(userLikeKey, UA)).isFalse();
        assertThat(redisTemplate.opsForSet().isMember(userLikeKey, UB)).isTrue();
    }

    // ============ flushLikeCountToMQ ============

    @Test
    void flushLikeCountToMQ_sendsRemarkIdCountVersionAndLastActivity() {
        remarkService.likeRemark(id, UA);

        remarkService.flushLikeCountToMQ();

        Map<String, Object> msg = captureMessageFor("remarkLikeCount.redis.queue");
        assertThat(msg)
                .containsEntry("remarkId", id)
                .containsEntry("likeCount", 1L)
                .containsEntry("version", 0L)             // 初始 version=0（DB 无记录）
                .containsKey("last_activity_at");
        assertThat(msg.get("last_activity_at")).asString().isNotBlank();
    }

    // ============ flushLikeUsersToMQ ============

    @Test
    void flushLikeUsersToMQ_sendsUsersVersionAndLastActivity() {
        remarkService.likeRemark(id, UA);
        remarkService.likeRemark(id, UB);

        remarkService.flushLikeUsersToMQ();

        Map<String, Object> msg = captureMessageFor("remarkLikeUsers.redis.queue");
        assertThat(msg)
                .containsEntry("remarkId", id)
                .containsEntry("version", 0L)
                .containsKey("last_activity_at");

        @SuppressWarnings("unchecked")
        Set<Long> users = (Set<Long>) msg.get("users");
        assertThat(users).containsExactlyInAnyOrder(UA, UB);
    }

    // ---- helpers ----

    /**
     * 捕获发往指定队列、且 remarkId 与当前测试一致的 MQ 消息。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> captureMessageFor(String queue) {
        org.mockito.ArgumentCaptor<Object> captor =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, org.mockito.Mockito.atLeastOnce())
                .convertAndSend(eq(queue), captor.capture());

        for (Object obj : captor.getAllValues()) {
            if (obj instanceof Map<?, ?> m && id.equals(m.get("remarkId"))) {
                return (Map<String, Object>) m;
            }
        }
        throw new AssertionError("No MQ message captured for queue=" + queue
                + " and remarkId=" + id + ", captured=" + captor.getAllValues());
    }
}
