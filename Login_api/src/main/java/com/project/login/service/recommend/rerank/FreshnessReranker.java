package com.project.login.service.recommend.rerank;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Order(20)
@RequiredArgsConstructor
public class FreshnessReranker implements Reranker {

    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> rerank(RecommendContext context, List<ContentCandidate> candidates) {
        for (ContentCandidate candidate : candidates) {
            LocalDateTime ts = resolveTime(candidate);
            if (ts == null) {
                continue;
            }
            long hours = Math.max(1, ChronoUnit.HOURS.between(ts, LocalDateTime.now()));
            double decay = Math.exp(-Math.log(2.0) * hours / Math.max(1.0, feedProperties.getFreshnessHalfLifeHours()));
            double boost = Math.min(feedProperties.getFreshnessBoostMax(), decay);
            candidate.setFinalScore(candidate.getFinalScore() > 0
                    ? candidate.getFinalScore() * (1.0 + boost)
                    : candidate.getRankScore() * (1.0 + boost));
            candidate.getFeatures().put("freshnessBoost", boost);
        }
        return candidates;
    }

    private LocalDateTime resolveTime(ContentCandidate candidate) {
        Object updatedAt = candidate.getFeatures().get("updatedAt");
        if (updatedAt instanceof LocalDateTime time) {
            return time;
        }
        Object createdAt = candidate.getFeatures().get("createdAt");
        if (createdAt instanceof LocalDateTime time) {
            return time;
        }
        return null;
    }
}
