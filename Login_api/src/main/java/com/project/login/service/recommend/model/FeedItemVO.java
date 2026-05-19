package com.project.login.service.recommend.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FeedItemVO {

    private ItemType itemType;
    private String itemId;
    private String title;
    private String summary;
    private Long authorId;
    private String authorName;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Integer answerCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String reason;
    private Double score;
    private String experimentId;
    private String variant;
    private String modelVersion;
    private String ranker;
}
