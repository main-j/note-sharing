package com.project.login.service.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "recommendation.infra")
public class RecommendationInfraProperties {

    private Topic topic = new Topic();
    private Redis redis = new Redis();
    private Event event = new Event();
    private Milvus milvus = new Milvus();
    private Onnx onnx = new Onnx();
    private Triton triton = new Triton();
    private Mlflow mlflow = new Mlflow();
    private Feast feast = new Feast();

    @Data
    public static class Topic {
        private String behavior = "rec_user_behavior";
        private String search = "rec_user_search";
        private String exposure = "rec_user_exposure";
        private String content = "rec_content_event";
    }

    @Data
    public static class Redis {
        private String fusedProfileKeyPrefix = "user_fused_profile:";
        private String recentSearchTermsKeyPrefix = "user_recent_search_terms:";
        private String recentActionsKeyPrefix = "user_recent_actions:";
        private String seenItemsKeyPrefix = "user_seen_items:";
        private String hotNotesKey = "hot_notes";
        private String itemRealtimeStatsKeyPrefix = "item_realtime_stats:";
    }

    @Data
    public static class Event {
        private boolean legacyRedisStreamEnabled = false;
    }

    @Data
    public static class Milvus {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 19530;
        private String collectionName = "recommend_item_vectors";
        private int topK = 60;
        private int vectorDim = 128;
    }

    @Data
    public static class Onnx {
        private boolean enabled = false;
        private String modelPath = "models/recommend_coarse_rank.onnx";
    }

    @Data
    public static class Triton {
        private boolean enabled = false;
        private String endpoint = "http://localhost:8000";
        private String modelName = "recommend_coarse_rank";
        private int timeoutMillis = 800;
    }

    @Data
    public static class Mlflow {
        private String trackingUri = "http://localhost:5001";
    }

    @Data
    public static class Feast {
        private boolean enabled = false;
        private String repoPath = "recommendation_offline/feast_repo";
        private String serverUrl = "http://localhost:6566";
        private int timeoutMillis = 800;
        private String userFeatureService = "user_features";
        private String itemFeatureService = "item_features";
    }
}
