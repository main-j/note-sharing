package com.project.login.service.recommend.event;

public final class RecommendKafkaTopics {

    private RecommendKafkaTopics() {
    }

    public static final String REC_USER_BEHAVIOR = "rec_user_behavior";
    public static final String REC_USER_SEARCH = "rec_user_search";
    public static final String REC_USER_EXPOSURE = "rec_user_exposure";
    public static final String REC_CONTENT_EVENT = "rec_content_event";
}
