package com.project.login.service.recommend.rerank;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;

import java.util.List;

public interface Reranker {

    List<ContentCandidate> rerank(RecommendContext context, List<ContentCandidate> candidates);
}
