package com.project.login.service.recommend.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class ContentCandidate {

    private String itemKey;
    private ItemType itemType;
    private String itemId;
    private Long authorId;
    private String source;

    private double recallScore;
    private double rankScore;
    private double finalScore;

    @Builder.Default
    private Map<String, Object> features = new HashMap<>();

    public static String buildItemKey(ItemType itemType, String itemId) {
        return itemType.name() + ":" + itemId;
    }
}
