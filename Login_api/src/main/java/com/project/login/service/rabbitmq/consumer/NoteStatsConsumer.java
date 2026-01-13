package com.project.login.service.rabbitmq.consumer;

import com.project.login.mapper.NoteStatsCompensationMapper;
import com.project.login.mapper.NoteStatsMapper;
import com.project.login.model.dataobject.NoteStatsCompensationDO;
import com.project.login.model.dataobject.NoteStatsDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteStatsConsumer {

    private final NoteStatsMapper noteStatsMapper;
    private final NoteStatsCompensationMapper compensationMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "note_stats:";

    @RabbitListener(queues = "note.redis.queue")
    public void process(Map<String, Object> data) {
        try {
            Long noteId = parseLongSafe(data.get("note_id"));
            if (noteId < 1) {
                log.warn("Invalid noteId={}, writing to compensation instead", noteId);
                writeCompensationSafe(data);
                return;
            }

            String authorName = String.valueOf(data.getOrDefault("authorName", ""));

            // 确保值不为负数
            long views = Math.max(0, parseLongSafe(data.getOrDefault("views", 0)));
            long likes = Math.max(0, parseLongSafe(data.getOrDefault("likes", 0)));
            long favorites = Math.max(0, parseLongSafe(data.getOrDefault("favorites", 0)));
            long comments = Math.max(0, parseLongSafe(data.getOrDefault("comments", 0)));
            LocalDateTime lastActivity = parseDateTimeSafe(data.get("last_activity_at"));
            long version = parseLongSafe(data.getOrDefault("version", 0));

            NoteStatsDO totals = new NoteStatsDO();
            totals.setNoteId(noteId);
            totals.setAuthorName(authorName);
            totals.setViews(views);
            totals.setLikes(likes);
            totals.setFavorites(favorites);
            totals.setComments(comments);
            totals.setLastActivityAt(lastActivity);
            totals.setVersion(version);

            // 1) 乐观锁全量更新
            int updated = noteStatsMapper.updateTotalsIfVersion(totals);
            if (updated > 0) {
                deleteIfCold(noteId, lastActivity);
                return;
            }

            // 2) 乐观锁失败 → 获取 DB 当前值
            NoteStatsDO db = noteStatsMapper.getById(noteId);
            if (db == null) {
                try {
                    noteStatsMapper.insert(totals);
                    deleteIfCold(noteId, lastActivity);
                    return;
                } catch (Exception e) {
                    log.warn("Insert failed for noteId={}, fallback to delta", noteId, e);
                }
            }

            // 3) 计算增量
            long dv = totals.getViews() - safe(db == null ? null : db.getViews());
            long dl = totals.getLikes() - safe(db == null ? null : db.getLikes());
            long df = totals.getFavorites() - safe(db == null ? null : db.getFavorites());
            long dc = totals.getComments() - safe(db == null ? null : db.getComments());

            boolean allNonPositive = dv <= 0 && dl <= 0 && df <= 0 && dc <= 0;
            if (allNonPositive &&
                    db != null &&
                    db.getLastActivityAt() != null &&
                    !db.getLastActivityAt().isBefore(lastActivity)) {

                deleteIfCold(noteId, lastActivity);
                return;
            }

            // 4) 增量更新
            NoteStatsDO deltas = new NoteStatsDO();
            deltas.setNoteId(noteId);
            deltas.setViews(Math.max(dv, 0));
            deltas.setLikes(Math.max(dl, 0));
            deltas.setFavorites(Math.max(df, 0));
            deltas.setComments(Math.max(dc, 0));
            deltas.setAuthorName(authorName);
            deltas.setLastActivityAt(lastActivity);

            int incUpdated = noteStatsMapper.incrementByDeltas(deltas);
            if (incUpdated > 0) {
                deleteIfCold(noteId, lastActivity);
                return;
            }

            // 5) 增量失败 → 写补偿
            writeCompensation(noteId, totals, authorName, lastActivity);

        } catch (Exception ex) {
            log.error("Error processing MQ data: {}", data, ex);
            writeCompensationSafe(data);
        }
    }

    private long safe(Long v) {
        return v == null ? 0L : v;
    }

    private Long parseLongSafe(Object obj) {
        try { return Long.parseLong(String.valueOf(obj)); }
        catch (Exception e) { return 0L; }
    }

    private LocalDateTime parseDateTimeSafe(Object obj) {
        try { return LocalDateTime.parse(String.valueOf(obj)); }
        catch (Exception e) { return LocalDateTime.now(); }
    }

    private void deleteIfCold(Long noteId, LocalDateTime incomingLast) {
        String key = REDIS_KEY_PREFIX + noteId;
        Object redisLastObj = redisTemplate.opsForHash().get(key, "last_activity_at");

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

    private void writeCompensation(Long noteId, NoteStatsDO totals, String authorName, LocalDateTime lastActivity) {
        NoteStatsCompensationDO comp = new NoteStatsCompensationDO();
        comp.setNoteId(noteId);
        comp.setAuthorName(authorName);
        comp.setViews(totals.getViews());
        comp.setLikes(totals.getLikes());
        comp.setFavorites(totals.getFavorites());
        comp.setComments(totals.getComments());
        comp.setLastActivityAt(lastActivity);
        comp.setStatus("PENDING");
        comp.setRetryCount(0);
        compensationMapper.insert(comp);
    }

    private void writeCompensationSafe(Map<String, Object> data) {
        try {
            Long noteId = parseLongSafe(data.get("note_id"));

            NoteStatsCompensationDO comp = new NoteStatsCompensationDO();
            comp.setNoteId(noteId < 1 ? 1 : noteId);
            comp.setAuthorName(String.valueOf(data.getOrDefault("authorName", "")));
            comp.setViews(parseLongSafe(data.getOrDefault("views", 0)));
            comp.setLikes(parseLongSafe(data.getOrDefault("likes", 0)));
            comp.setFavorites(parseLongSafe(data.getOrDefault("favorites", 0)));
            comp.setComments(parseLongSafe(data.getOrDefault("comments", 0)));
            comp.setLastActivityAt(parseDateTimeSafe(data.get("last_activity_at")));
            comp.setStatus("PENDING");
            comp.setRetryCount(0);
            compensationMapper.insert(comp);

        } catch (Exception e) {
            log.error("Failed to write compensation for message {}", data, e);
        }
    }
}