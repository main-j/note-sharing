package com.project.login.service.recommend.recall;

import com.project.login.model.dto.search.NoteSearchDTO;
import com.project.login.model.vo.NoteSearchVO;
import com.project.login.model.vo.qa.QuestionVO;
import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import com.project.login.service.search.SearchQAService;
import com.project.login.service.search.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchIntentRecallSource implements RecallSource {

    private final SearchService searchService;
    private final SearchQAService searchQAService;
    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> recall(RecommendContext context) {
        List<String> terms = context.getRecentSearchTerms();
        if (terms == null || terms.isEmpty()) {
            terms = context.getTopTags();
        }
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }

        int limit = feedProperties.getMaxRecallPerSource();
        List<ContentCandidate> candidates = new ArrayList<>();
        for (String term : terms.stream().limit(8).toList()) {
            NoteSearchDTO dto = new NoteSearchDTO();
            dto.setKeyword(term);
            List<NoteSearchVO> notes = searchService.searchNotes(dto);
            notes.stream().limit(Math.max(10, limit / 20)).forEach(note ->
                    candidates.add(toNoteCandidate(note, "SEARCH_INTENT", 1.8, term)));

            List<QuestionVO> questions = searchQAService.searchQuestions(term);
            questions.stream().limit(Math.max(10, limit / 20)).forEach(q ->
                    candidates.add(toQuestionCandidate(q, "SEARCH_INTENT", 1.7, term)));
        }
        return candidates;
    }

    private ContentCandidate toNoteCandidate(NoteSearchVO note, String source, double recallScore, String term) {
        java.util.Map<String, Object> features = new java.util.HashMap<>();
        features.put("title", note.getTitle());
        features.put("summary", note.getContentSummary());
        features.put("authorName", note.getAuthorName());
        features.put("views", note.getViewCount());
        features.put("likes", note.getLikeCount());
        features.put("favorites", note.getFavoriteCount());
        features.put("comments", note.getCommentCount());
        features.put("updatedAt", note.getUpdatedAt());
        features.put("matchedTerm", term);
        return ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId(String.valueOf(note.getNoteId()))
                .itemKey(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(note.getNoteId())))
                .source(source)
                .recallScore(recallScore)
                .features(features)
                .build();
    }

    private ContentCandidate toQuestionCandidate(QuestionVO q, String source, double recallScore, String term) {
        java.util.Map<String, Object> features = new java.util.HashMap<>();
        features.put("title", q.getTitle());
        features.put("summary", q.getContent());
        features.put("tags", q.getTags());
        features.put("likes", q.getLikeCount());
        features.put("favorites", q.getFavoriteCount());
        features.put("answers", q.getAnswerCount() != null ? q.getAnswerCount() :
                (q.getAnswers() == null ? 0 : q.getAnswers().size()));
        features.put("createdAt", q.getCreatedAt());
        features.put("authorName", q.getAuthorName());
        features.put("matchedTerm", term);
        return ContentCandidate.builder()
                .itemType(ItemType.QUESTION)
                .itemId(q.getQuestionId())
                .itemKey(ContentCandidate.buildItemKey(ItemType.QUESTION, q.getQuestionId()))
                .authorId(q.getAuthorId())
                .source(source)
                .recallScore(recallScore)
                .features(features)
                .build();
    }
}
