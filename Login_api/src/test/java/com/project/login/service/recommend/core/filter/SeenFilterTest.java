package com.project.login.service.recommend.core.filter;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SeenFilterTest {

    private final SeenFilter seenFilter = new SeenFilter();

    @Test
    void allow_shouldRejectSeenItem() {
        RecommendContext context = RecommendContext.builder()
                .userId(1L)
                .seenItemKeys(Set.of("NOTE:100"))
                .build();
        ContentCandidate candidate = ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId("100")
                .itemKey("NOTE:100")
                .build();

        assertThat(seenFilter.allow(context, candidate)).isFalse();
    }

    @Test
    void allow_shouldIgnoreSeenItemsForSearchScene() {
        RecommendContext context = RecommendContext.builder()
                .userId(1L)
                .scene("SEARCH")
                .seenItemKeys(Set.of("NOTE:100"))
                .build();
        ContentCandidate candidate = ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId("100")
                .itemKey("NOTE:100")
                .build();

        assertThat(seenFilter.allow(context, candidate)).isTrue();
    }
}
