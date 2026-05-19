package com.project.login.service.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "recommendation.experiment")
public class RecommendationExperimentProperties {

    private boolean enabled = true;
    private String experimentId = "recommend_rank_v1";
    private double modelRankRatio = 0.0;
    private String controlVariant = "control_rule";
    private String treatmentVariant = "treatment_model";
    private String defaultModelVersion = "pending";
    private String stateFile = "data/pipeline/rollout_state.json";
    private double autoRollbackErrorRateThreshold = 0.05;
}
