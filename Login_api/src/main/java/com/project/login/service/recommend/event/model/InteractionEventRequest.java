package com.project.login.service.recommend.event.model;

import com.project.login.service.recommend.event.RecommendEventType;
import com.project.login.service.recommend.model.ItemType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class InteractionEventRequest {

    @NotNull
    private Long userId;
    @NotNull
    private RecommendEventType eventType;

    private ItemType itemType;
    private String itemId;
    private Long authorId;
    private String requestId;
    private String source;
    private Integer position;
    private List<String> tags;
    private String keyword;
    private String scene;
    private String experimentId;
    private String variant;
    private String modelVersion;
    private String ranker;
}
