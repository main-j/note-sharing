package com.project.login.service.recommend.rank;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RuleRanker implements Ranker {

    @Override
    public List<ContentCandidate> rank(RecommendContext context, List<ContentCandidate> candidates) {
        for (ContentCandidate candidate : candidates) {
            ensureMutableFeatures(candidate);
            double hotScore = getLong(candidate, "likes") * 1.0
                    + getLong(candidate, "favorites") * 1.4
                    + getLong(candidate, "comments") * 1.8
                    + getLong(candidate, "answers") * 1.2
                    + getLong(candidate, "views") * 0.1;

            double freshnessScore = recencyScore(candidate);
            double tagMatchScore = getDouble(candidate, "tagMatchScore");
            double followBoost = "FOLLOW".equals(candidate.getSource()) || (candidate.getSource() != null && candidate.getSource().contains("FOLLOW"))
                    ? 1.2 : 0.0;

            double rankScore = candidate.getRecallScore() * 2.0 + hotScore * 0.1 + freshnessScore + tagMatchScore + followBoost;
            candidate.getFeatures().put("ruleRankScore", rankScore);
            if (candidate.getRankScore() > 0) {
                candidate.setRankScore(candidate.getRankScore() * 0.8 + rankScore * 0.2);
            } else {
                candidate.setRankScore(rankScore);
            }
        }
        return candidates;
    }

    private void ensureMutableFeatures(ContentCandidate candidate) {
        Map<String, Object> features = candidate.getFeatures();
        if (features == null || features.isEmpty()) {
            candidate.setFeatures(new HashMap<>());
            return;
        }
        if (!(features instanceof HashMap)) {
            candidate.setFeatures(new HashMap<>(features));
        }
    }

    private long getLong(ContentCandidate c, String key) {
        Object val = c.getFeatures().get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private double getDouble(ContentCandidate c, String key) {
        Object val = c.getFeatures().get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private double recencyScore(ContentCandidate candidate) {
        Object updatedAt = candidate.getFeatures().get("updatedAt");
        Object createdAt = candidate.getFeatures().get("createdAt");
        Object t = updatedAt != null ? updatedAt : createdAt;
        if (!(t instanceof LocalDateTime dateTime)) {
            return 0.0;
        }
        long hours = Math.max(1, ChronoUnit.HOURS.between(dateTime, LocalDateTime.now()));
        return 12.0 / hours;
    }
}
