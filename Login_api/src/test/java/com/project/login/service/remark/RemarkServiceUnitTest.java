package com.project.login.service.remark;

import com.project.login.controller.RemarkWebSocketController;
import com.project.login.convert.RemarkConvert;
import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.RemarkCountDO;
import com.project.login.repository.RemarkLikeByUsersRepository;
import com.project.login.repository.RemarkLikeCountRepository;
import com.project.login.repository.RemarkRepository;
import com.project.login.service.notestats.NoteStatsService;
import com.project.login.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RemarkService 单元测试（参考 NoteStats 模式重写）：
 *  - Redis schema 改为 stats Hash + user set，对 HashOperations 做 mock
 *  - 验证 initStatsIfNeeded read-through（write path 也会回填，避免计数腰斩）
 *  - version 不再由 like/cancel 维护，仅在 read-through 时从 DB 加载
 *  - 校验 last_activity_at 在每次写操作中被更新
 */
@ExtendWith(MockitoExtension.class)
class RemarkServiceUnitTest {

    private static final String ID = "remark-001";
    private static final String STATS_KEY = "remark_stats:" + ID;
    private static final String USER_LIKE_KEY = "remark_user_like:" + ID;
    private static final String LIKE_COUNT_QUEUE = "remarkLikeCount.redis.queue";
    private static final String LIKE_USERS_QUEUE = "remarkLikeUsers.redis.queue";
    private static final String F_COUNT = "count";
    private static final String F_VERSION = "version";
    private static final String F_LAST_ACTIVITY = "last_activity_at";
    private static final Long UA = 1001L;
    private static final Long UB = 1002L;
    /** 与 RemarkService.STATS_TTL 保持一致 */
    private static final Duration TTL = Duration.ofHours(2);

    @Mock private RemarkRepository remarkRepository;
    @Mock private RemarkLikeByUsersRepository remarkLikeByUsersRepository;
    @Mock private RemarkConvert remarkConvert;
    @Mock private NoteMapper noteMapper;
    @Mock private UserMapper userMapper;
    @Mock private NoteStatsService noteStatsService;
    @Mock private RemarkLikeCountRepository remarkLikeCountRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private NotificationService notificationService;
    @Mock private RemarkWebSocketController remarkWebSocketController;

    @Mock private SetOperations<String, Object> setOps;
    @Mock private HashOperations<String, Object, Object> hashOps;

    @InjectMocks private RemarkService remarkService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        // 默认 Repository 无数据；个别用例会覆盖
        lenient().when(remarkLikeCountRepository.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(remarkLikeByUsersRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    // ============ likeRemark ============

    @Test
    void like_firstTime_initializesStatsHashAndIncrementsCount() {
        // Redis stats 不存在 → initStatsIfNeeded 应从 DB 加载（这里 DB 也没有），写入 count=0, version=0
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(false);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(false);
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(false);
        when(hashOps.increment(STATS_KEY, F_COUNT, 1L)).thenReturn(1L);

        assertThat(remarkService.likeRemark(ID, UA)).isTrue();

        // initStatsIfNeeded 把 count/version/last_activity_at 写入 Hash
        verify(hashOps).put(STATS_KEY, F_COUNT, "0");
        verify(hashOps).put(STATS_KEY, F_VERSION, "0");
        // last_activity_at 在 initStatsIfNeeded 与 likeRemark 各写一次 → 共 2 次
        verify(hashOps, org.mockito.Mockito.times(2))
                .put(eq(STATS_KEY), eq(F_LAST_ACTIVITY), anyString());

        // 业务逻辑：set 添加用户 + HINCRBY count +1
        verify(setOps).add(USER_LIKE_KEY, UA);
        verify(hashOps).increment(STATS_KEY, F_COUNT, 1L);
        // TTL 在 initStatsIfNeeded 与 likeRemark 中都会刷新（保活），共 2 次
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).expire(USER_LIKE_KEY, TTL);
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).expire(STATS_KEY, TTL);
        // 不再有 version 单独的 key 或递增
        verify(hashOps, never()).increment(eq(STATS_KEY), eq(F_VERSION), anyLong());
    }

    @Test
    void like_readThrough_loadsExistingCountAndVersionFromMongo() {
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(false);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(false);
        when(remarkLikeCountRepository.findById(ID)).thenReturn(Optional.of(
                RemarkCountDO.builder().remarkId(ID).remarkLikeCount(42L).version(3L).build()));
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(false);
        when(hashOps.increment(STATS_KEY, F_COUNT, 1L)).thenReturn(43L);

        assertThat(remarkService.likeRemark(ID, UA)).isTrue();

        // 关键：count=42 (而非 0!) 从 DB 加载进 Hash，避免缓存缺失时把计数腰斩
        verify(hashOps).put(STATS_KEY, F_COUNT, "42");
        verify(hashOps).put(STATS_KEY, F_VERSION, "3");
        verify(hashOps).increment(STATS_KEY, F_COUNT, 1L);
    }

    @Test
    void like_whenAlreadyLikedInSet_returnsFalseWithoutMutation() {
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(true);
        when(hashOps.size(STATS_KEY)).thenReturn(3L);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(true);
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(true);

        assertThat(remarkService.likeRemark(ID, UA)).isFalse();

        verify(setOps, never()).add(any(), any());
        verify(hashOps, never()).increment(eq(STATS_KEY), eq(F_COUNT), anyLong());
    }

    @Test
    void like_negativeCountFromIncrement_resetToZero() {
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(true);
        when(hashOps.size(STATS_KEY)).thenReturn(3L);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(true);
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(false);
        when(hashOps.increment(STATS_KEY, F_COUNT, 1L)).thenReturn(-3L);

        assertThat(remarkService.likeRemark(ID, UA)).isTrue();

        // 检测到负值后复位为 "0"
        verify(hashOps).put(STATS_KEY, F_COUNT, "0");
    }

    // ============ cancelLikeRemark ============

    @Test
    void cancel_whenLiked_removesUserAndDecrementsCount() {
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(true);
        when(hashOps.size(STATS_KEY)).thenReturn(3L);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(true);
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(true);
        when(hashOps.increment(STATS_KEY, F_COUNT, -1L)).thenReturn(0L);

        assertThat(remarkService.cancelLikeRemark(ID, UA)).isTrue();

        verify(setOps).remove(USER_LIKE_KEY, UA);
        verify(hashOps).increment(STATS_KEY, F_COUNT, -1L);
        verify(hashOps).put(eq(STATS_KEY), eq(F_LAST_ACTIVITY), anyString());
        verify(hashOps, never()).increment(eq(STATS_KEY), eq(F_VERSION), anyLong());
    }

    @Test
    void cancel_whenNotLikedInSet_returnsFalseWithoutMutation() {
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(true);
        when(hashOps.size(STATS_KEY)).thenReturn(3L);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(true);
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(false);

        assertThat(remarkService.cancelLikeRemark(ID, UA)).isFalse();

        verify(setOps, never()).remove(any(), any());
        verify(hashOps, never()).increment(eq(STATS_KEY), eq(F_COUNT), anyLong());
    }

    @Test
    void cancel_decrementBelowZero_clampedToZero() {
        when(redisTemplate.hasKey(STATS_KEY)).thenReturn(true);
        when(hashOps.size(STATS_KEY)).thenReturn(3L);
        when(redisTemplate.hasKey(USER_LIKE_KEY)).thenReturn(true);
        when(setOps.isMember(USER_LIKE_KEY, UA)).thenReturn(true);
        when(hashOps.increment(STATS_KEY, F_COUNT, -1L)).thenReturn(-1L);

        assertThat(remarkService.cancelLikeRemark(ID, UA)).isTrue();

        verify(hashOps).put(STATS_KEY, F_COUNT, "0");
    }

    // ============ flushLikeCountToMQ ============

    @Test
    void flushLikeCountToMQ_sendsCountVersionAndLastActivity() {
        when(redisTemplate.keys("remark_stats:*")).thenReturn(Set.of(STATS_KEY));
        when(hashOps.entries(STATS_KEY)).thenReturn(Map.of(
                F_COUNT, "5",
                F_VERSION, "3",
                F_LAST_ACTIVITY, "2025-01-01T10:00:00"
        ));

        remarkService.flushLikeCountToMQ();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(LIKE_COUNT_QUEUE), captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) captor.getValue();
        assertThat(msg)
                .containsEntry("remarkId", ID)
                .containsEntry("likeCount", 5L)
                .containsEntry("version", 3L)
                .containsEntry("last_activity_at", "2025-01-01T10:00:00");
    }

    @Test
    void flushLikeCountToMQ_noKeys_noMessageSent() {
        when(redisTemplate.keys("remark_stats:*")).thenReturn(Set.of());

        remarkService.flushLikeCountToMQ();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ============ flushLikeUsersToMQ ============

    @Test
    void flushLikeUsersToMQ_sendsUsersVersionAndLastActivity() {
        when(redisTemplate.keys("remark_user_like:*")).thenReturn(Set.of(USER_LIKE_KEY));
        when(setOps.members(USER_LIKE_KEY)).thenReturn(Set.of(UA, UB));
        when(hashOps.entries(STATS_KEY)).thenReturn(Map.of(
                F_VERSION, "4",
                F_LAST_ACTIVITY, "2025-02-02T11:00:00"
        ));

        remarkService.flushLikeUsersToMQ();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(LIKE_USERS_QUEUE), captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) captor.getValue();
        assertThat(msg)
                .containsEntry("remarkId", ID)
                .containsEntry("version", 4L)
                .containsEntry("last_activity_at", "2025-02-02T11:00:00");

        @SuppressWarnings("unchecked")
        Set<Long> users = (Set<Long>) msg.get("users");
        assertThat(users).containsExactlyInAnyOrder(UA, UB);
    }
}
