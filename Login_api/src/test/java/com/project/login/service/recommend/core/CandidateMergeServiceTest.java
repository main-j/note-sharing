package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMergeServiceTest {

    private final CandidateMergeService mergeService = new CandidateMergeService();

    @Test
    void merge_shouldDeduplicateByItemKeyAndKeepMaxRecallScore() {
        ContentCandidate hot = ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId("1")
                .itemKey("NOTE:1")
                .source("HOT")
                .recallScore(1.0)
                .features(Map.of("likes", 1))
                .build();
        ContentCandidate tag = ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId("1")
                .itemKey("NOTE:1")
                .source("TAG")
                .recallScore(2.0)
                .features(Map.of("views", 10))
                .build();

        List<ContentCandidate> merged = mergeService.merge(List.of(hot, tag));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getRecallScore()).isEqualTo(2.0);
        assertThat(merged.get(0).getSource()).contains("HOT");
        assertThat(merged.get(0).getSource()).contains("TAG");
        assertThat(merged.get(0).getFeatures()).containsEntry("likes", 1);
        assertThat(merged.get(0).getFeatures()).containsEntry("views", 10);
    }
}
