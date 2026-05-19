package com.project.login.service.recommend.rerank;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(10)
@RequiredArgsConstructor
public class DiversityReranker implements Reranker {

    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> rerank(RecommendContext context, List<ContentCandidate> candidates) {
        Map<String, Integer> sourceQuota = new HashMap<>();
        List<ContentCandidate> result = new ArrayList<>(candidates.size());
        List<ContentCandidate> overflow = new ArrayList<>();
        for (ContentCandidate candidate : candidates) {
            String source = candidate.getSource() == null ? "UNKNOWN" : candidate.getSource().split(",")[0];
            int count = sourceQuota.getOrDefault(source, 0);
            if (count < feedProperties.getDiversityPerSourceLimit()) {
                sourceQuota.put(source, count + 1);
                result.add(candidate);
            } else {
                overflow.add(candidate);
            }
        }
        result.addAll(overflow);
        return result;
    }
}
