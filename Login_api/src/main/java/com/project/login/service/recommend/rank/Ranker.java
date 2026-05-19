package com.project.login.service.recommend.rank;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;

import java.util.List;

public interface Ranker {

    List<ContentCandidate> rank(RecommendContext context, List<ContentCandidate> candidates);
}
