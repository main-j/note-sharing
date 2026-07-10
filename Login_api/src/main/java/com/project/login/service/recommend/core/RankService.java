package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import com.project.login.service.recommend.rank.Ranker;
import com.project.login.service.recommend.rank.RuleRanker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankService {

    private final List<Ranker> rankers;

    public List<ContentCandidate> rank(RecommendContext context, List<ContentCandidate> candidates) {
        List<ContentCandidate> ranked = candidates;
        for (Ranker ranker : rankers) {
            if (!context.isModelEnabled() && !(ranker instanceof RuleRanker)) {
                continue;
            }
            // Keep search relevance stable: ES order + rule features, skip model rankers.
            if ("SEARCH".equalsIgnoreCase(context.getScene()) && !(ranker instanceof RuleRanker)) {
                continue;
            }
            ranked = ranker.rank(context, ranked);
        }

        ranked.forEach(candidate -> candidate.setFinalScore(candidate.getRankScore() > 0
                ? candidate.getRankScore()
                : candidate.getRecallScore()));

        return ranked.stream()
                .sorted(Comparator.comparingDouble(ContentCandidate::getFinalScore).reversed())
                .toList();
    }
}
