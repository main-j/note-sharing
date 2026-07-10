package com.project.login.service.recommend.rerank;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(30)
@RequiredArgsConstructor
public class FeedMixReranker implements Reranker {

    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> rerank(RecommendContext context, List<ContentCandidate> candidates) {
        if ("SEARCH".equalsIgnoreCase(context.getScene())) {
            return candidates;
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int pageSize = context.getPageSize() == null ? candidates.size() : context.getPageSize();
        int targetNotes = Math.min(candidates.size(),
                Math.max(feedProperties.getMinNoteCount(), (int) Math.round(pageSize * feedProperties.getNoteMixRatio())));
        int targetQuestions = Math.min(candidates.size() - targetNotes,
                Math.max(feedProperties.getMinQuestionCount(), pageSize - targetNotes));

        List<ContentCandidate> notes = new ArrayList<>();
        List<ContentCandidate> questions = new ArrayList<>();
        for (ContentCandidate candidate : candidates) {
            if (candidate.getItemType() == ItemType.QUESTION) {
                questions.add(candidate);
            } else {
                notes.add(candidate);
            }
        }

        List<ContentCandidate> mixed = new ArrayList<>(candidates.size());
        int ni = 0;
        int qi = 0;
        while (mixed.size() < candidates.size()) {
            if (ni < notes.size() && countType(mixed, ItemType.NOTE) < targetNotes) {
                mixed.add(notes.get(ni++));
                continue;
            }
            if (qi < questions.size() && countType(mixed, ItemType.QUESTION) < targetQuestions) {
                mixed.add(questions.get(qi++));
                continue;
            }
            if (ni < notes.size()) {
                mixed.add(notes.get(ni++));
            } else if (qi < questions.size()) {
                mixed.add(questions.get(qi++));
            } else {
                break;
            }
        }
        return mixed;
    }

    private int countType(List<ContentCandidate> candidates, ItemType type) {
        int count = 0;
        for (ContentCandidate candidate : candidates) {
            if (candidate.getItemType() == type) {
                count++;
            }
        }
        return count;
    }
}
