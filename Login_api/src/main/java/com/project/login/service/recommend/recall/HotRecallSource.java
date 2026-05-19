package com.project.login.service.recommend.recall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.NoteStatsMapper;
import com.project.login.mapper.NotebookMapper;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.model.dataobject.NoteStatsDO;
import com.project.login.model.dataobject.QuestionDO;
import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HotRecallSource implements RecallSource {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NoteStatsMapper noteStatsMapper;
    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper noteSpaceMapper;
    private final MongoTemplate mongoTemplate;
    private final RecommendationFeedProperties feedProperties;
    private final RecommendationInfraProperties infraProperties;

    @Override
    public List<ContentCandidate> recall(RecommendContext context) {
        int limit = feedProperties.getMaxRecallPerSource();
        List<ContentCandidate> candidates = new ArrayList<>();
        candidates.addAll(recallHotNotes(limit));
        candidates.addAll(recallHotQuestions(Math.max(80, limit / 3)));
        return candidates;
    }

    private List<ContentCandidate> recallHotNotes(int limit) {
        List<Long> noteIds = readHotNoteIdsFromRedis();
        if (noteIds.isEmpty()) {
            noteIds = noteStatsMapper.getHotNotes(limit).stream()
                    .map(NoteStatsDO::getNoteId)
                    .toList();
        }
        if (noteIds.isEmpty()) {
            return List.of();
        }

        Map<Long, NoteDO> noteMap = noteIds.stream()
                .map(noteMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(NoteDO::getId, Function.identity(), (a, b) -> a));

        Map<Long, NoteStatsDO> statsMap = noteStatsMapper.getByIds(noteIds).stream()
                .collect(Collectors.toMap(NoteStatsDO::getNoteId, Function.identity(), (a, b) -> a));

        List<ContentCandidate> result = new ArrayList<>();
        for (Long noteId : noteIds) {
            NoteDO note = noteMap.get(noteId);
            if (note == null) {
                continue;
            }
            NoteStatsDO stats = statsMap.get(noteId);
            Long spaceId = notebookMapper.selectSpaceIdByNotebookId(note.getNotebookId());
            Long authorId = spaceId == null ? null : noteSpaceMapper.selectUserIdBySpaceId(spaceId);
            Map<String, Object> features = new HashMap<>();
            features.put("title", note.getTitle());
            features.put("updatedAt", note.getUpdatedAt());
            if (stats != null) {
                features.put("authorName", stats.getAuthorName());
                features.put("views", stats.getViews());
                features.put("likes", stats.getLikes());
                features.put("favorites", stats.getFavorites());
                features.put("comments", stats.getComments());
            }
            result.add(ContentCandidate.builder()
                    .itemType(ItemType.NOTE)
                    .itemId(String.valueOf(noteId))
                    .itemKey(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(noteId)))
                    .authorId(authorId)
                    .source("HOT")
                    .recallScore(1.4)
                    .features(features)
                    .build());
        }
        return result;
    }

    private List<ContentCandidate> recallHotQuestions(int limit) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(Math.max(limit * 3, 120));
        List<QuestionDO> questions = mongoTemplate.find(query, QuestionDO.class);
        if (questions.isEmpty()) {
            return List.of();
        }
        return questions.stream()
                .sorted(Comparator.comparingDouble(this::questionHotScore).reversed())
                .limit(limit)
                .map(q -> {
                    Map<String, Object> features = new HashMap<>();
                    features.put("title", q.getTitle());
                    features.put("summary", q.getContent());
                    features.put("tags", q.getTags());
                    features.put("likes", (long) q.getLikes().size());
                    features.put("favorites", (long) q.getFavorites().size());
                    features.put("answers", (long) q.getAnswers().size());
                    features.put("createdAt", q.getCreatedAt());
                    return ContentCandidate.builder()
                            .itemType(ItemType.QUESTION)
                            .itemId(q.getQuestionId())
                            .itemKey(ContentCandidate.buildItemKey(ItemType.QUESTION, q.getQuestionId()))
                            .authorId(q.getAuthorId())
                            .source("HOT")
                            .recallScore(1.3)
                            .features(features)
                            .build();
                }).toList();
    }

    private double questionHotScore(QuestionDO q) {
        return q.getLikes().size() * 1.0 + q.getFavorites().size() * 1.5 + q.getAnswers().size() * 2.0;
    }

    private List<Long> readHotNoteIdsFromRedis() {
        String json = redisTemplate.opsForValue().get(infraProperties.getRedis().getHotNotesKey());
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignore) {
            return List.of();
        }
    }
}
