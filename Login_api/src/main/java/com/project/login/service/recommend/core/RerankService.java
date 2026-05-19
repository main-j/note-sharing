package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import com.project.login.service.recommend.rerank.Reranker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RerankService {

    private final List<Reranker> rerankers;

    public List<ContentCandidate> rerank(RecommendContext context, List<ContentCandidate> candidates) {
        List<ContentCandidate> reranked = candidates;
        for (Reranker reranker : rerankers) {
            reranked = reranker.rerank(context, reranked);
        }
        return reranked;
    }
}
