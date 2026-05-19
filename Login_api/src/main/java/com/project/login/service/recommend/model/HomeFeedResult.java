package com.project.login.service.recommend.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HomeFeedResult {

    private String requestId;
    private String scene;
    private String experimentId;
    private String variant;
    private String modelVersion;
    private Boolean modelEnabled;
    private List<FeedItemVO> items;
}
