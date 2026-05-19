package com.project.login.service.recommend.event.model;

import com.project.login.service.recommend.event.RecommendEventType;
import com.project.login.service.recommend.model.ItemType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class RecommendEvent {

    private String eventId;
    private String requestId;
    private Long userId;
    private ItemType itemType;
    private String itemId;
    private Long authorId;
    private RecommendEventType eventType;
    private String source;
    private Integer position;
    private String scene;
    private String experimentId;
    private String variant;
    private String modelVersion;
    private String ranker;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private Long timestamp;
    private String keyword;
}
