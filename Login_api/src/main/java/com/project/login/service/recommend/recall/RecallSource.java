package com.project.login.service.recommend.recall;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;

import java.util.List;

public interface RecallSource {

    List<ContentCandidate> recall(RecommendContext context);

    default String sourceName() {
        return getClass().getSimpleName();
    }
}
