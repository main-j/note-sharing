package com.project.login.service.recommend.core;

import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.NoteStatsMapper;
import com.project.login.mapper.NotebookMapper;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.model.dataobject.NoteStatsDO;
import com.project.login.model.dataobject.QuestionDO;
import com.project.login.repository.QuestionRepository;
import com.project.login.service.recommend.feature.FeastFeatureClient;
import com.project.login.service.recommend.feature.RedisFeatureClient;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeatureHydrationService {

    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper noteSpaceMapper;
    private final NoteStatsMapper noteStatsMapper;
    private final QuestionRepository questionRepository;
    private final RedisFeatureClient redisFeatureClient;
    private final FeastFeatureClient feastFeatureClient;

    public List<ContentCandidate> hydrate(RecommendContext context, List<ContentCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, NoteDO> noteMap = loadNotes(candidates);
        Map<Long, NoteStatsDO> noteStatsMap = loadNoteStats(candidates);
        Map<String, QuestionDO> questionMap = loadQuestions(candidates);

        List<ContentCandidate> hydrated = new ArrayList<>(candidates.size());
        for (ContentCandidate candidate : candidates) {
            Map<String, Object> merged = new HashMap<>();
            if (candidate.getFeatures() != null) {
                merged.putAll(candidate.getFeatures());
            }

            if (candidate.getItemType() == ItemType.NOTE) {
                hydrateNote(candidate, noteMap, noteStatsMap, merged);
            } else if (candidate.getItemType() == ItemType.QUESTION) {
                hydrateQuestion(candidate, questionMap, merged);
            }
            candidate.setFeatures(merged);
            redisFeatureClient.mergeRealtimeStats(candidate);
            hydrated.add(candidate);
        }
        feastFeatureClient.enrich(context, hydrated);
        return hydrated;
    }

    private Map<Long, NoteDO> loadNotes(List<ContentCandidate> candidates) {
        List<Long> noteIds = candidates.stream()
                .filter(candidate -> candidate.getItemType() == ItemType.NOTE)
                .map(candidate -> parseLong(candidate.getItemId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return noteIds.stream()
                .map(noteMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(NoteDO::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, NoteStatsDO> loadNoteStats(List<ContentCandidate> candidates) {
        List<Long> noteIds = candidates.stream()
                .filter(candidate -> candidate.getItemType() == ItemType.NOTE)
                .map(candidate -> parseLong(candidate.getItemId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        return noteStatsMapper.getByIds(noteIds).stream()
                .collect(Collectors.toMap(NoteStatsDO::getNoteId, Function.identity(), (a, b) -> a));
    }

    private Map<String, QuestionDO> loadQuestions(List<ContentCandidate> candidates) {
        List<String> questionIds = candidates.stream()
                .filter(candidate -> candidate.getItemType() == ItemType.QUESTION)
                .map(ContentCandidate::getItemId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        return questionIds.stream()
                .map(questionRepository::findByQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(QuestionDO::getQuestionId, Function.identity(), (a, b) -> a));
    }

    private void hydrateNote(ContentCandidate candidate,
                             Map<Long, NoteDO> noteMap,
                             Map<Long, NoteStatsDO> noteStatsMap,
                             Map<String, Object> features) {
        Long noteId = parseLong(candidate.getItemId());
        if (noteId == null) {
            return;
        }
        NoteDO note = noteMap.get(noteId);
        if (note != null) {
            features.putIfAbsent("title", note.getTitle());
            features.putIfAbsent("createdAt", note.getCreatedAt());
            features.putIfAbsent("updatedAt", note.getUpdatedAt());
            Long spaceId = notebookMapper.selectSpaceIdByNotebookId(note.getNotebookId());
            Long authorId = spaceId == null ? null : noteSpaceMapper.selectUserIdBySpaceId(spaceId);
            if (candidate.getAuthorId() == null && authorId != null) {
                candidate.setAuthorId(authorId);
            }
        }

        NoteStatsDO stats = noteStatsMap.get(noteId);
        if (stats != null) {
            features.putIfAbsent("authorName", stats.getAuthorName());
            features.putIfAbsent("views", stats.getViews());
            features.putIfAbsent("likes", stats.getLikes());
            features.putIfAbsent("favorites", stats.getFavorites());
            features.putIfAbsent("comments", stats.getComments());
        }
    }

    private void hydrateQuestion(ContentCandidate candidate,
                                 Map<String, QuestionDO> questionMap,
                                 Map<String, Object> features) {
        QuestionDO question = questionMap.get(candidate.getItemId());
        if (question == null) {
            return;
        }
        if (candidate.getAuthorId() == null) {
            candidate.setAuthorId(question.getAuthorId());
        }
        features.putIfAbsent("title", question.getTitle());
        features.putIfAbsent("summary", question.getContent());
        features.putIfAbsent("createdAt", question.getCreatedAt());
        features.putIfAbsent("tags", question.getTags() == null ? List.of() : question.getTags());
        features.putIfAbsent("likes", question.getLikes() == null ? 0 : question.getLikes().size());
        features.putIfAbsent("favorites", question.getFavorites() == null ? 0 : question.getFavorites().size());
        features.putIfAbsent("answers", question.getAnswers() == null ? 0 : question.getAnswers().size());
    }

    private Long parseLong(String text) {
        try {
            return text == null ? null : Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
