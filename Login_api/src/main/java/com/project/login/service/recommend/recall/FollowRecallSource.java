package com.project.login.service.recommend.recall;

import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.NotebookMapper;
import com.project.login.mapper.NoteStatsMapper;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.model.dataobject.NoteStatsDO;
import com.project.login.model.dataobject.QuestionDO;
import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FollowRecallSource implements RecallSource {

    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper noteSpaceMapper;
    private final NoteStatsMapper noteStatsMapper;
    private final MongoTemplate mongoTemplate;
    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> recall(RecommendContext context) {
        List<Long> followeeIds = context.getFolloweeIds();
        if (followeeIds == null || followeeIds.isEmpty()) {
            return List.of();
        }

        int limit = feedProperties.getMaxRecallPerSource();
        int noteLimit = Math.max(50, limit / 2);
        int questionLimit = Math.max(50, limit / 2);

        List<ContentCandidate> candidates = new ArrayList<>();
        candidates.addAll(recallFollowNotes(followeeIds, noteLimit));
        candidates.addAll(recallFollowQuestions(followeeIds, questionLimit));
        return candidates;
    }

    private List<ContentCandidate> recallFollowNotes(List<Long> followeeIds, int limit) {
        List<NoteDO> notes = noteMapper.selectRecentPublishedByAuthorIds(followeeIds, limit);
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }

        Map<Long, NoteStatsDO> statsMap = noteStatsMapper.getByIds(
                notes.stream().map(NoteDO::getId).toList()
        ).stream().collect(Collectors.toMap(NoteStatsDO::getNoteId, Function.identity(), (a, b) -> a));

        List<ContentCandidate> result = new ArrayList<>(notes.size());
        for (NoteDO note : notes) {
            Long spaceId = notebookMapper.selectSpaceIdByNotebookId(note.getNotebookId());
            Long authorId = spaceId == null ? null : noteSpaceMapper.selectUserIdBySpaceId(spaceId);
            NoteStatsDO stats = statsMap.get(note.getId());
            result.add(ContentCandidate.builder()
                    .itemType(ItemType.NOTE)
                    .itemId(String.valueOf(note.getId()))
                    .itemKey(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(note.getId())))
                    .authorId(authorId)
                    .source("FOLLOW")
                    .recallScore(2.5)
                    .features(buildNoteFeatures(note, stats))
                    .build());
        }
        return result;
    }

    private List<ContentCandidate> recallFollowQuestions(List<Long> followeeIds, int limit) {
        Query query = Query.query(Criteria.where("authorId").in(followeeIds))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);

        List<QuestionDO> questions = mongoTemplate.find(query, QuestionDO.class);
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }

        return questions.stream().map(q -> ContentCandidate.builder()
                .itemType(ItemType.QUESTION)
                .itemId(q.getQuestionId())
                .itemKey(ContentCandidate.buildItemKey(ItemType.QUESTION, q.getQuestionId()))
                .authorId(q.getAuthorId())
                .source("FOLLOW")
                .recallScore(2.4)
                .features(buildQuestionFeatures(q))
                .build()).toList();
    }

    private java.util.Map<String, Object> buildNoteFeatures(NoteDO note, NoteStatsDO stats) {
        java.util.Map<String, Object> features = new java.util.HashMap<>();
        features.put("title", note.getTitle());
        features.put("updatedAt", note.getUpdatedAt());
        if (stats != null) {
            features.put("views", stats.getViews());
            features.put("likes", stats.getLikes());
            features.put("favorites", stats.getFavorites());
            features.put("comments", stats.getComments());
            features.put("authorName", stats.getAuthorName());
        }
        return features;
    }

    private java.util.Map<String, Object> buildQuestionFeatures(QuestionDO question) {
        java.util.Map<String, Object> features = new java.util.HashMap<>();
        features.put("title", question.getTitle());
        features.put("summary", question.getContent());
        features.put("createdAt", question.getCreatedAt());
        features.put("tags", question.getTags());
        features.put("likes", (long) question.getLikes().size());
        features.put("favorites", (long) question.getFavorites().size());
        features.put("answers", (long) question.getAnswers().size());
        return features;
    }
}
