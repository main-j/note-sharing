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
public class TagRecallSource implements RecallSource {

    private final SearchService searchService;
    private final SearchQAService searchQAService;
    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> recall(RecommendContext context) {
        List<String> tags = context.getTopTags();
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        int limit = feedProperties.getMaxRecallPerSource();
        List<ContentCandidate> candidates = new ArrayList<>();

        for (String term : tags.stream().limit(8).toList()) {
            NoteSearchDTO dto = new NoteSearchDTO();
            dto.setKeyword(term);
            List<NoteSearchVO> notes = searchService.searchNotes(dto);
            notes.stream().limit(Math.max(10, limit / 20)).forEach(note ->
                    candidates.add(toNoteCandidate(note, "TAG", 1.6)));

            List<QuestionVO> questions = searchQAService.searchQuestions(term);
            questions.stream().limit(Math.max(10, limit / 20)).forEach(q ->
                    candidates.add(toQuestionCandidate(q, "TAG", 1.5)));
        }
        return candidates;
    }

    private ContentCandidate toNoteCandidate(NoteSearchVO note, String source, double recallScore) {
        return ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId(String.valueOf(note.getNoteId()))
                .itemKey(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(note.getNoteId())))
                .source(source)
                .recallScore(recallScore)
                .features(new java.util.HashMap<>() {{
                    put("title", note.getTitle());
                    put("summary", note.getContentSummary());
                    put("authorName", note.getAuthorName());
                    put("views", note.getViewCount());
                    put("likes", note.getLikeCount());
                    put("favorites", note.getFavoriteCount());
                    put("comments", note.getCommentCount());
                    put("updatedAt", note.getUpdatedAt());
                }})
                .build();
    }

    private ContentCandidate toQuestionCandidate(QuestionVO q, String source, double recallScore) {
        return ContentCandidate.builder()
                .itemType(ItemType.QUESTION)
                .itemId(q.getQuestionId())
                .itemKey(ContentCandidate.buildItemKey(ItemType.QUESTION, q.getQuestionId()))
                .authorId(q.getAuthorId())
                .source(source)
                .recallScore(recallScore)
                .features(new java.util.HashMap<>() {{
                    put("title", q.getTitle());
                    put("summary", q.getContent());
                    put("tags", q.getTags());
                    put("likes", q.getLikeCount());
                    put("favorites", q.getFavoriteCount());
                    put("answers", q.getAnswerCount() != null ? q.getAnswerCount() :
                            (q.getAnswers() == null ? 0 : q.getAnswers().size()));
                    put("createdAt", q.getCreatedAt());
                    put("authorName", q.getAuthorName());
                }})
                .build();
    }
}
