package com.project.login.service.rabbitmq.consumer;

import com.project.login.model.dataobject.RemarkCountDO;
import com.project.login.model.dataobject.RemarkLikeByUsersDO;
import com.project.login.repository.RemarkLikeByUsersRepository;
import com.project.login.repository.RemarkLikeCountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RemarkConsumer 单元测试。
 * 校验：
 *   - Layer 1（version 一致时直接覆盖）
 *   - Layer 2（version 冲突时 delta / overwrite + 乐观锁重试）
 *   - deleteIfCold（成功落盘后清理 Redis，触发下一次 read-through）
 *   - 空 remarkId 防御
 *   - 不写补偿表（参考 NoteStats 但去除该机制）
 */
class RemarkConsumerTest {

    private static final String ID = "remark-001";
    private static final String STATS_KEY = "remark_stats:" + ID;
    private static final String USER_KEY = "remark_user_like:" + ID;

    private RemarkLikeCountRepository countRepo;
    private RemarkLikeByUsersRepository usersRepo;
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hops = mock(HashOperations.class);

    private RemarkConsumer remarkConsumer;

    @BeforeEach
    void setUp() {
        countRepo = mock(RemarkLikeCountRepository.class);
        usersRepo = mock(RemarkLikeByUsersRepository.class);
        when(redisTemplate.opsForHash()).thenReturn(hops);
        remarkConsumer = new RemarkConsumer(countRepo, usersRepo, redisTemplate);
    }

    // ============ message factories ============

    private Map<String, Object> countMsg(long likeCount, long version) {
        Map<String, Object> m = new HashMap<>();
        m.put("remarkId", ID);
        m.put("likeCount", likeCount);
        m.put("version", version);
        m.put("last_activity_at", LocalDateTime.now().toString());
        return m;
    }

    private Map<String, Object> usersMsg(Set<Long> users, long version) {
        Map<String, Object> m = new HashMap<>();
        m.put("remarkId", ID);
        m.put("users", users);
        m.put("version", version);
        m.put("last_activity_at", LocalDateTime.now().toString());
        return m;
    }

    private RemarkCountDO countDoc(long likeCount, long version) {
        return RemarkCountDO.builder().remarkId(ID).remarkLikeCount(likeCount).version(version).build();
    }

    // ============ count layer 1 / 2 ============

    @Test
    void countCreateNewAndDeletesColdRedis() {
        when(countRepo.findById(ID)).thenReturn(Optional.empty());

        remarkConsumer.handleLikeCountMessage(countMsg(5L, 0L));

        verify(countRepo).save(any(RemarkCountDO.class));
        // Redis stats Hash 中 last_activity_at 缺失 → 视为冷数据，应被删
        verify(redisTemplate).delete(STATS_KEY);
    }

    @Test
    void countLayer1_overwriteWhenVersionMatches() {
        when(countRepo.findById(ID)).thenReturn(Optional.of(countDoc(3L, 3L)));

        remarkConsumer.handleLikeCountMessage(countMsg(7L, 3L));

        verify(countRepo).save(argThat(c -> c.getRemarkLikeCount() == 7L));
    }

    @Test
    void countLayer2_deltaWhenVersionMismatches() {
        when(countRepo.findById(ID)).thenReturn(Optional.of(countDoc(3L, 5L)));

        remarkConsumer.handleLikeCountMessage(countMsg(10L, 3L));

        verify(countRepo, atLeastOnce()).save(argThat(c -> c.getRemarkLikeCount() == 10L));
    }

    @Test
    void countLayer2_retriesOnOptimisticLockFailure() {
        when(countRepo.findById(ID)).thenReturn(Optional.of(countDoc(3L, 5L)));
        when(countRepo.save(any(RemarkCountDO.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        remarkConsumer.handleLikeCountMessage(countMsg(10L, 3L));

        // Layer 1 失败 1 次 + Layer 2 重试 3 次 = 至少 2 次 save 调用
        verify(countRepo, atLeast(2)).save(any(RemarkCountDO.class));
    }

    @Test
    void countSuccess_redisHotterThanMessage_keepsRedis() {
        when(countRepo.findById(ID)).thenReturn(Optional.empty());
        // Redis 中 last_activity_at 比消息的 5min 之后（更新过），不该被删
        when(hops.get(STATS_KEY, "last_activity_at"))
                .thenReturn(LocalDateTime.now().plusMinutes(5).toString());

        remarkConsumer.handleLikeCountMessage(countMsg(5L, 0L));

        verify(redisTemplate, never()).delete(STATS_KEY);
    }

    // ============ users layer 1 / 2 ============

    @Test
    void usersCreateNewAndDeletesColdRedis() {
        when(usersRepo.findById(ID)).thenReturn(Optional.empty());

        remarkConsumer.handleLikeUsersMessage(usersMsg(Set.of(1001L, 1002L), 0L));

        verify(usersRepo).save(any(RemarkLikeByUsersDO.class));
        verify(redisTemplate).delete(USER_KEY);
    }

    @Test
    void usersLayer1_overwriteWhenVersionMatches() {
        when(usersRepo.findById(ID)).thenReturn(Optional.of(
                RemarkLikeByUsersDO.builder().remarkId(ID).userList(Set.of(1001L)).version(2L).build()));

        remarkConsumer.handleLikeUsersMessage(usersMsg(Set.of(1001L, 1002L), 2L));

        verify(usersRepo).save(argThat(u -> u.getUserList().contains(1002L)));
    }

    @Test
    void usersLayer2_overwriteWhenVersionMismatches() {
        when(usersRepo.findById(ID)).thenReturn(Optional.of(
                RemarkLikeByUsersDO.builder().remarkId(ID).userList(Set.of(1001L)).version(5L).build()));

        remarkConsumer.handleLikeUsersMessage(usersMsg(Set.of(1002L, 1003L), 2L));

        verify(usersRepo, atLeastOnce()).save(argThat(u ->
                u.getUserList().contains(1002L) && u.getUserList().contains(1003L)));
    }

    @Test
    void usersLayer2_retriesOnOptimisticLockFailure() {
        when(usersRepo.findById(ID)).thenReturn(Optional.of(
                RemarkLikeByUsersDO.builder().remarkId(ID).userList(Set.of()).version(5L).build()));
        when(usersRepo.save(any(RemarkLikeByUsersDO.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        remarkConsumer.handleLikeUsersMessage(usersMsg(Set.of(1001L), 2L));

        verify(usersRepo, atLeast(2)).save(any(RemarkLikeByUsersDO.class));
    }

    // ============ defensive ============

    @Test
    void skipOnEmptyRemarkId() {
        Map<String, Object> bad = new HashMap<>();
        bad.put("remarkId", "");
        bad.put("likeCount", 1L);
        bad.put("version", 0L);
        bad.put("last_activity_at", LocalDateTime.now().toString());

        remarkConsumer.handleLikeCountMessage(bad);
        remarkConsumer.handleLikeUsersMessage(bad);

        verify(countRepo, never()).save(any());
        verify(usersRepo, never()).save(any());
        verify(redisTemplate, never()).delete(STATS_KEY);
        verify(redisTemplate, never()).delete(USER_KEY);
    }
}
