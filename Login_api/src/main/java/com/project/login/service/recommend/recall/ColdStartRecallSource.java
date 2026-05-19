package com.project.login.service.recommend.recall;

import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.NoteStatsMapper;
import com.project.login.mapper.NotebookMapper;
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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ColdStartRecallSource implements RecallSource {

    private final NoteMapper noteMapper;
    private final NoteStatsMapper noteStatsMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper noteSpaceMapper;
    private final MongoTemplate mongoTemplate;
    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> recall(RecommendContext context) {
        boolean coldUser = (context.getTopTags() == null || context.getTopTags().isEmpty())
                && (context.getRecentSearchTerms() == null || context.getRecentSearchTerms().isEmpty())
                && (context.getFolloweeIds() == null || context.getFolloweeIds().isEmpty());
        if (!coldUser) {
            return List.of();
        }

        int limit = Math.max(30, feedProperties.getMaxRecallPerSource() / 4);
        List<ContentCandidate> candidates = new ArrayList<>();
        candidates.addAll(recallRecentNotes(limit));
        candidates.addAll(recallRecentQuestions(Math.max(20, limit / 2)));
        return candidates;
    }

    private List<ContentCandidate> recallRecentNotes(int limit) {
        List<NoteDO> notes = noteMapper.selectRecentPublished(limit);
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        Map<Long, NoteStatsDO> statsMap = noteStatsMapper.getByIds(
                notes.stream().map(NoteDO::getId).toList()
        ).stream().collect(Collectors.toMap(NoteStatsDO::getNoteId, Function.identity(), (a, b) -> a));

        List<ContentCandidate> result = new ArrayList<>();
        for (NoteDO note : notes) {
            Long spaceId = notebookMapper.selectSpaceIdByNotebookId(note.getNotebookId());
            Long authorId = spaceId == null ? null : noteSpaceMapper.selectUserIdBySpaceId(spaceId);
            Map<String, Object> features = new HashMap<>();
            features.put("title", note.getTitle());
            features.put("updatedAt", note.getUpdatedAt());
            NoteStatsDO stats = statsMap.get(note.getId());
            if (stats != null) {
                features.put("views", stats.getViews());
                features.put("likes", stats.getLikes());
                features.put("favorites", stats.getFavorites());
                features.put("comments", stats.getComments());
            }
            result.add(ContentCandidate.builder()
                    .itemType(ItemType.NOTE)
                    .itemId(String.valueOf(note.getId()))
                    .itemKey(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(note.getId())))
                    .authorId(authorId)
                    .source("COLD_START")
                    .recallScore(1.0)
                    .features(features)
                    .build());
        }
        return result;
    }

    private List<ContentCandidate> recallRecentQuestions(int limit) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        List<QuestionDO> questions = mongoTemplate.find(query, QuestionDO.class);
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        return questions.stream().map(q -> {
            Map<String, Object> features = new HashMap<>();
            features.put("title", q.getTitle());
            features.put("summary", q.getContent());
            features.put("createdAt", q.getCreatedAt());
            features.put("tags", q.getTags());
            return ContentCandidate.builder()
                    .itemType(ItemType.QUESTION)
                    .itemId(q.getQuestionId())
                    .itemKey(ContentCandidate.buildItemKey(ItemType.QUESTION, q.getQuestionId()))
                    .authorId(q.getAuthorId())
                    .source("COLD_START")
                    .recallScore(0.9)
                    .features(features)
                    .build();
        }).toList();
    }
}
