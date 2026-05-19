package com.project.login.service.recommend.rerank;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessRerankerTest {

  private final FreshnessReranker reranker = new FreshnessReranker(new RecommendationFeedProperties());

    @Test
    void rerank_shouldBoostRecentItems() {
        Map<String, Object> features = new HashMap<>();
        features.put("updatedAt", LocalDateTime.now().minusHours(2));
        ContentCandidate candidate = ContentCandidate.builder()
                .itemKey("NOTE:1")
                .rankScore(1.0)
                .finalScore(1.0)
                .features(features)
                .build();

        List<ContentCandidate> reranked = reranker.rerank(RecommendContext.builder().userId(1L).build(), List.of(candidate));
        assertThat(reranked.get(0).getFinalScore()).isGreaterThan(1.0);
        assertThat(reranked.get(0).getFeatures()).containsKey("freshnessBoost");
    }
}
