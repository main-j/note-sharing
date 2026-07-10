package com.project.login.service.hot;

import com.project.login.model.vo.NoteSearchVO;
import com.project.login.repository.NoteRepository;
import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NoteStatsMapper;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.model.dataobject.NoteStatsDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HotService {

    private final StringRedisTemplate redisTemplate;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;
    private final NoteStatsMapper noteStatsMapper;
    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;

    private static final String HOT_NOTE_KEY = "hot_notes";
    private static final String REDIS_KEY_PREFIX = "note_stats:";

    public HotService(StringRedisTemplate redisTemplate,
                      RedisTemplate<String, Object> redis,
                      ObjectMapper objectMapper,
                      NoteStatsMapper noteStatsMapper,
                      NoteRepository noteRepository,
                      NoteMapper noteMapper) {
        this.redisTemplate = redisTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.noteStatsMapper = noteStatsMapper;
        this.noteRepository = noteRepository;
        this.noteMapper = noteMapper;
    }

    public List<NoteSearchVO> getHotNotesDetail() {
        List<Long> hotNoteIds = getHotNoteIds();
        System.out.println(hotNoteIds);
        if (hotNoteIds.isEmpty()) return Collections.emptyList();

        Map<Long, NoteSearchVO> voMap = new LinkedHashMap<>();

        // 使用 Elasticsearch 批量查询，未命中时回退 MySQL
        for (Long noteId : hotNoteIds) {
            noteRepository.findById(noteId).ifPresentOrElse(entity -> {
                NoteSearchVO vo = new NoteSearchVO();
                vo.setNoteId(noteId);
                vo.setTitle(entity.getTitle());
                vo.setContentSummary(entity.getContentSummary());
                voMap.put(noteId, vo);
            }, () -> {
                NoteDO note = noteMapper.selectById(noteId);
                if (note == null) {
                    return;
                }
                NoteSearchVO vo = new NoteSearchVO();
                vo.setNoteId(noteId);
                vo.setTitle(note.getTitle());
                vo.setContentSummary(note.getTitle());
                voMap.put(noteId, vo);
            });
        }

        // 批量加载状态
        Map<Long, NoteStatsDO> statsMap = loadStatsFromRedisThenDB(hotNoteIds);

        // 写入 VO
        voMap.forEach((id, vo) -> {
            NoteStatsDO stats = statsMap.get(id);
            if (stats != null) {
                vo.setAuthorName(stats.getAuthorName());
                vo.setViewCount(stats.getViews().intValue());
                vo.setLikeCount(stats.getLikes().intValue());
                vo.setFavoriteCount(stats.getFavorites().intValue());
                vo.setCommentCount(stats.getComments().intValue());
            }
        });

        // 保持热门顺序
        return hotNoteIds.stream()
                .map(voMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    private List<Long> getHotNoteIds() {
        try {
            String json = redisTemplate.opsForValue().get(HOT_NOTE_KEY);
            if (json != null && !json.isEmpty()) {
                // 清理可能的格式问题：去除前后空白
                json = json.trim();
                
                // 处理多个 JSON 值连接的情况（如 [1,2][3,4] 或 [1,2]-[3,4]）
                if (json.startsWith("[")) {
                    // 找到第一个完整的 JSON 数组的结束位置
                    int bracketCount = 0;
                    int endIndex = -1;
                    for (int i = 0; i < json.length(); i++) {
                        char c = json.charAt(i);
                        if (c == '[') bracketCount++;
                        if (c == ']') bracketCount--;
                        if (bracketCount == 0 && i > 0) {
                            endIndex = i;
                            break;
                        }
                    }
                    if (endIndex > 0 && endIndex < json.length() - 1) {
                        // 如果后面还有内容，只取第一个完整的 JSON 数组
                        json = json.substring(0, endIndex + 1);
                    }
                }
                
                // 验证 JSON 格式：必须是有效的数组格式
                if (!json.startsWith("[") || !json.endsWith("]")) {
                    throw new IllegalArgumentException("Redis 中的数据不是有效的 JSON 数组格式: " + json);
                }
                
                return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            }
        } catch (Exception e) {
            // 记录详细的错误信息，包括原始 JSON 数据
            String json = redisTemplate.opsForValue().get(HOT_NOTE_KEY);
            System.err.println("========== 解析热榜数据失败 ==========");
            System.err.println("原始数据: " + json);
            System.err.println("数据长度: " + (json != null ? json.length() : 0));
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            System.err.println("=====================================");
        }
        return Collections.emptyList();
    }

    private Map<Long, NoteStatsDO> loadStatsFromRedisThenDB(List<Long> noteIds) {
        Map<Long, NoteStatsDO> result = new HashMap<>();
        HashOperations<String, Object, Object> ops = redis.opsForHash();

        for (Long id : noteIds) {
            String key = REDIS_KEY_PREFIX + id;
            Map<Object, Object> map = ops.entries(key);

            if (map == null || map.isEmpty()) {
                // Redis 未命中 → 查询 MySQL
                NoteStatsDO db = noteStatsMapper.getById(id);
                if (db == null) db = defaultStats(id);
                writeStatsToRedis(key, ops, db);
                map = ops.entries(key);
            }

            result.put(id, mapToStats(id, map));
        }

        return result;
    }

    private void writeStatsToRedis(String key, HashOperations<String, Object, Object> ops, NoteStatsDO db) {
        ops.put(key, "authorName", db.getAuthorName());
        ops.put(key, "views", String.valueOf(db.getViews()));
        ops.put(key, "likes", String.valueOf(db.getLikes()));
        ops.put(key, "favorites", String.valueOf(db.getFavorites()));
        ops.put(key, "comments", String.valueOf(db.getComments()));
        redis.expire(key, 7, java.util.concurrent.TimeUnit.DAYS);
    }

    private NoteStatsDO mapToStats(Long id, Map<Object, Object> map) {
        NoteStatsDO stats = new NoteStatsDO();
        stats.setNoteId(id);
        stats.setAuthorName(map.getOrDefault("authorName", "未知作者").toString());
        stats.setViews(Long.parseLong(map.getOrDefault("views", "0").toString()));
        stats.setLikes(Long.parseLong(map.getOrDefault("likes", "0").toString()));
        stats.setFavorites(Long.parseLong(map.getOrDefault("favorites", "0").toString()));
        stats.setComments(Long.parseLong(map.getOrDefault("comments", "0").toString()));
        return stats;
    }

    private NoteStatsDO defaultStats(Long noteId) {
        NoteStatsDO stats = new NoteStatsDO();
        stats.setNoteId(noteId);
        stats.setAuthorName("未知作者");
        stats.setViews(0L);
        stats.setLikes(0L);
        stats.setFavorites(0L);
        stats.setComments(0L);
        return stats;
    }
}
