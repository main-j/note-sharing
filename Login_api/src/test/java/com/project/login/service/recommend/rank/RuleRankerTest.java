package com.project.login.service.recommend.rank;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRankerTest {

    private final RuleRanker ranker = new RuleRanker();

    @Test
    void rank_shouldProducePositiveScoreForHotCandidate() {
        ContentCandidate candidate = ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId("1")
                .itemKey("NOTE:1")
                .source("HOT")
                .recallScore(1.5)
                .features(Map.of(
                        "likes", 10L,
                        "favorites", 5L,
                        "comments", 2L
                ))
                .build();

        List<ContentCandidate> ranked = ranker.rank(RecommendContext.builder().userId(1L).build(), List.of(candidate));
        assertThat(ranked.get(0).getRankScore()).isGreaterThan(0);
        assertThat(ranked.get(0).getFeatures()).containsKey("ruleRankScore");
    }
}
