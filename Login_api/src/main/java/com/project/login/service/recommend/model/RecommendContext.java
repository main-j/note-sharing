package com.project.login.service.recommend.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class RecommendContext {

    private Long userId;
    private Integer pageSize;
    private String scene;
    private String requestId;
    private String experimentId;
    private String variant;
    private String modelVersion;
    private boolean modelEnabled;

    @Builder.Default
    private List<String> topTags = new ArrayList<>();

    @Builder.Default
    private List<String> recentSearchTerms = new ArrayList<>();

    @Builder.Default
    private List<Long> followeeIds = new ArrayList<>();

    @Builder.Default
    private Set<String> seenItemKeys = new HashSet<>();

    @Builder.Default
    private List<String> recentItemKeys = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
