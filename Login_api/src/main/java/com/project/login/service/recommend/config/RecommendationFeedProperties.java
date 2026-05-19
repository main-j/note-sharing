package com.project.login.service.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "recommendation.feed")
public class RecommendationFeedProperties {

    private int maxRecallPerSource = 250;
    private int seenTtlDays = 14;
    private int recentActionSize = 200;
    private int diversityPerSourceLimit = 6;
    private double freshnessHalfLifeHours = 12.0;
    private double freshnessBoostMax = 1.2;
    private double noteMixRatio = 0.7;
    private int minQuestionCount = 3;
    private int minNoteCount = 10;
}
