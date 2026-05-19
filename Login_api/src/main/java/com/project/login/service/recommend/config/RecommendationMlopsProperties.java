package com.project.login.service.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "recommendation.mlops")
public class RecommendationMlopsProperties {

    private String serviceUrl = "http://localhost:8090";
    private int timeoutMillis = 3000;
    private String modelName = "recommend_coarse_rank";
    private String modelType = "lightgbm";
}
