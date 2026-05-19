package com.project.login.service.recommend;

import com.project.login.model.dto.search.NoteSearchDTO;
import com.project.login.model.vo.NoteSearchVO;
import com.project.login.model.vo.qa.QuestionVO;
import com.project.login.service.recommend.core.CandidateFilterService;
import com.project.login.service.recommend.core.CandidateMergeService;
import com.project.login.service.recommend.core.FeatureHydrationService;
import com.project.login.service.recommend.core.QueryHydrationService;
import com.project.login.service.recommend.core.RankService;
import com.project.login.service.recommend.core.RerankService;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import com.project.login.service.search.SearchQAService;
import com.project.login.service.search.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonalizedSearchRecommendationService {

    private final SearchService searchService;
    private final SearchQAService searchQAService;
    private final QueryHydrationService queryHydrationService;
    private final CandidateMergeService candidateMergeService;
    private final CandidateFilterService candidateFilterService;
    private final FeatureHydrationService featureHydrationService;
    private final RankService rankService;
    private final RerankService rerankService;

    public List<NoteSearchVO> searchNotes(NoteSearchDTO dto, Long userId) {
        List<NoteSearchVO> base = searchService.searchNotes(dto);
        if (userId == null || base.isEmpty()) {
            return base;
        }
        RecommendContext context = queryHydrationService.hydrate(
                userId,
                Math.max(1, Math.min(base.size(), 50)),
                "SEARCH",
                null,
                dto.getKeyword() == null ? List.of() : List.of(dto.getKeyword()));
        List<ContentCandidate> candidates = toNoteCandidates(base, dto.getKeyword());
        return toNoteSearchVOs(base, runRecommendPipeline(context, candidates));
    }

    public List<QuestionVO> searchQuestions(String keyword, Long userId) {
        List<QuestionVO> base = searchQAService.searchQuestions(keyword);
        if (userId == null || base.isEmpty()) {
            return base;
        }
        RecommendContext context = queryHydrationService.hydrate(
                userId,
                Math.max(1, Math.min(base.size(), 50)),
                "SEARCH",
                null,
                keyword == null ? List.of() : List.of(keyword));
        List<ContentCandidate> candidates = toQuestionCandidates(base, keyword);
        return toQuestionVOs(base, runRecommendPipeline(context, candidates));
    }

    private List<ContentCandidate> runRecommendPipeline(RecommendContext context, List<ContentCandidate> candidates) {
        List<ContentCandidate> merged = candidateMergeService.merge(candidates);
        List<ContentCandidate> filtered = candidateFilterService.filter(context, merged);
        List<ContentCandidate> hydrated = featureHydrationService.hydrate(context, filtered);
        List<ContentCandidate> ranked = rankService.rank(context, hydrated);
        return rerankService.rerank(context, ranked);
    }

    private List<ContentCandidate> toNoteCandidates(List<NoteSearchVO> notes, String keyword) {
        List<ContentCandidate> candidates = new ArrayList<>(notes.size());
        int total = Math.max(1, notes.size());
        for (int i = 0; i < notes.size(); i++) {
            NoteSearchVO note = notes.get(i);
            if (note.getNoteId() == null) {
                continue;
            }
            Map<String, Object> features = new HashMap<>();
            features.put("title", note.getTitle());
            features.put("summary", note.getContentSummary());
            features.put("authorName", note.getAuthorName());
            features.put("views", note.getViewCount() == null ? 0 : note.getViewCount());
            features.put("likes", note.getLikeCount() == null ? 0 : note.getLikeCount());
            features.put("favorites", note.getFavoriteCount() == null ? 0 : note.getFavoriteCount());
            features.put("comments", note.getCommentCount() == null ? 0 : note.getCommentCount());
            features.put("updatedAt", note.getUpdatedAt());
            features.put("tagMatchScore", keyword == null || keyword.isBlank() ? 0.0 : 1.0);
            double recallScore = 1.0 - ((double) i / total);
            candidates.add(ContentCandidate.builder()
                    .itemType(ItemType.NOTE)
                    .itemId(String.valueOf(note.getNoteId()))
                    .itemKey(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(note.getNoteId())))
                    .source("SEARCH")
                    .recallScore(Math.max(0.1, recallScore))
                    .features(features)
                    .build());
        }
        return candidates;
    }

    private List<ContentCandidate> toQuestionCandidates(List<QuestionVO> questions, String keyword) {
        List<ContentCandidate> candidates = new ArrayList<>(questions.size());
        int total = Math.max(1, questions.size());
        for (int i = 0; i < questions.size(); i++) {
            QuestionVO question = questions.get(i);
            if (question.getQuestionId() == null || question.getQuestionId().isBlank()) {
                continue;
            }
            Map<String, Object> features = new HashMap<>();
            features.put("title", question.getTitle());
            features.put("summary", question.getContent());
            features.put("tags", question.getTags() == null ? List.of() : question.getTags());
            features.put("authorName", question.getAuthorName());
            features.put("likes", question.getLikeCount() == null ? 0 : question.getLikeCount());
            features.put("favorites", question.getFavoriteCount() == null ? 0 : question.getFavoriteCount());
            features.put("answers", question.getAnswerCount() == null ? 0 : question.getAnswerCount());
            features.put("createdAt", question.getCreatedAt());
            features.put("tagMatchScore", keyword == null || keyword.isBlank() ? 0.0 : 1.0);
            double recallScore = 1.0 - ((double) i / total);
            candidates.add(ContentCandidate.builder()
                    .itemType(ItemType.QUESTION)
                    .itemId(question.getQuestionId())
                    .itemKey(ContentCandidate.buildItemKey(ItemType.QUESTION, question.getQuestionId()))
                    .authorId(question.getAuthorId())
                    .source("SEARCH")
                    .recallScore(Math.max(0.1, recallScore))
                    .features(features)
                    .build());
        }
        return candidates;
    }

    private List<NoteSearchVO> toNoteSearchVOs(List<NoteSearchVO> base, List<ContentCandidate> reranked) {
        Map<Long, NoteSearchVO> existing = new HashMap<>();
        for (NoteSearchVO note : base) {
            if (note.getNoteId() != null) {
                existing.put(note.getNoteId(), note);
            }
        }
        List<NoteSearchVO> result = new ArrayList<>();
        for (ContentCandidate candidate : reranked) {
            if (candidate.getItemType() != ItemType.NOTE) {
                continue;
            }
            Long noteId = parseLong(candidate.getItemId());
            if (noteId == null) {
                continue;
            }
            NoteSearchVO vo = existing.getOrDefault(noteId, new NoteSearchVO());
            vo.setNoteId(noteId);
            Map<String, Object> features = candidate.getFeatures();
            if (features != null) {
                if (features.get("title") != null) vo.setTitle(String.valueOf(features.get("title")));
                if (features.get("summary") != null) vo.setContentSummary(String.valueOf(features.get("summary")));
                if (features.get("authorName") != null) vo.setAuthorName(String.valueOf(features.get("authorName")));
                if (features.get("views") instanceof Number n) vo.setViewCount(n.intValue());
                if (features.get("likes") instanceof Number n) vo.setLikeCount(n.intValue());
                if (features.get("favorites") instanceof Number n) vo.setFavoriteCount(n.intValue());
                if (features.get("comments") instanceof Number n) vo.setCommentCount(n.intValue());
                if (features.get("updatedAt") instanceof java.time.LocalDateTime t) vo.setUpdatedAt(t);
            }
            result.add(vo);
        }
        return result;
    }

    private List<QuestionVO> toQuestionVOs(List<QuestionVO> base, List<ContentCandidate> reranked) {
        Map<String, QuestionVO> existing = new HashMap<>();
        for (QuestionVO question : base) {
            if (question.getQuestionId() != null) {
                existing.put(question.getQuestionId(), question);
            }
        }
        List<QuestionVO> result = new ArrayList<>();
        for (ContentCandidate candidate : reranked) {
            if (candidate.getItemType() != ItemType.QUESTION || candidate.getItemId() == null) {
                continue;
            }
            QuestionVO vo = existing.getOrDefault(candidate.getItemId(), new QuestionVO());
            vo.setQuestionId(candidate.getItemId());
            Map<String, Object> features = candidate.getFeatures();
            if (features != null) {
                if (features.get("title") != null) vo.setTitle(String.valueOf(features.get("title")));
                if (features.get("summary") != null) vo.setContent(String.valueOf(features.get("summary")));
                if (features.get("authorName") != null) vo.setAuthorName(String.valueOf(features.get("authorName")));
                if (features.get("tags") instanceof List<?> tags) vo.setTags(tags.stream().map(String::valueOf).toList());
                if (features.get("likes") instanceof Number n) vo.setLikeCount(n.intValue());
                if (features.get("favorites") instanceof Number n) vo.setFavoriteCount(n.intValue());
                if (features.get("answers") instanceof Number n) vo.setAnswerCount(n.intValue());
                if (features.get("createdAt") instanceof java.time.LocalDateTime t) vo.setCreatedAt(t);
            }
            if (vo.getAnswers() == null) {
                vo.setAnswers(List.of());
            }
            result.add(vo);
        }
        return result;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (Exception ignore) {
            return null;
        }
    }
}
