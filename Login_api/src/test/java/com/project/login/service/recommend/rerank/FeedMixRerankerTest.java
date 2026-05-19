package com.project.login.service.recommend.rerank;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedMixRerankerTest {

    private final FeedMixReranker reranker = new FeedMixReranker(new RecommendationFeedProperties());

    @Test
    void rerank_shouldInterleaveNotesAndQuestions() {
        List<ContentCandidate> candidates = List.of(
                note("1"), note("2"), note("3"), note("4"), note("5"),
                question("q1"), question("q2")
        );
        List<ContentCandidate> mixed = reranker.rerank(
                RecommendContext.builder().userId(1L).pageSize(7).build(),
                candidates
        );
        long questionCount = mixed.stream().filter(c -> c.getItemType() == ItemType.QUESTION).count();
        assertThat(questionCount).isGreaterThanOrEqualTo(2);
        assertThat(mixed).hasSize(7);
    }

    private ContentCandidate note(String id) {
        return ContentCandidate.builder().itemType(ItemType.NOTE).itemId(id).itemKey("NOTE:" + id).build();
    }

    private ContentCandidate question(String id) {
        return ContentCandidate.builder().itemType(ItemType.QUESTION).itemId(id).itemKey("QUESTION:" + id).build();
    }
}
