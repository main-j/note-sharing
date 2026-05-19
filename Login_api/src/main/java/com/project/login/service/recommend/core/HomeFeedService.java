package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.FeedItemVO;
import com.project.login.service.recommend.model.HomeFeedResult;
import com.project.login.service.recommend.model.RecommendContext;
import com.project.login.service.recommend.event.ExposureLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeFeedService {

    private final QueryHydrationService queryHydrationService;
    private final RecallOrchestrator recallOrchestrator;
    private final CandidateMergeService candidateMergeService;
    private final CandidateFilterService candidateFilterService;
    private final FeatureHydrationService featureHydrationService;
    private final RankService rankService;
    private final RerankService rerankService;
    private final FeedSelectionService feedSelectionService;
    private final ExposureLogService exposureLogService;

    public HomeFeedResult recommend(Long userId, int pageSize) {
        RecommendContext context = queryHydrationService.hydrate(userId, pageSize);
        List<ContentCandidate> recalled = recallOrchestrator.recall(context);
        List<ContentCandidate> merged = candidateMergeService.merge(recalled);
        List<ContentCandidate> filtered = candidateFilterService.filter(context, merged);
        List<ContentCandidate> hydrated = featureHydrationService.hydrate(context, filtered);
        List<ContentCandidate> ranked = rankService.rank(context, hydrated);
        List<ContentCandidate> reranked = rerankService.rerank(context, ranked);
        List<FeedItemVO> feedItems = feedSelectionService.select(context, reranked);
        exposureLogService.logExposures(context, feedItems);
        return HomeFeedResult.builder()
                .requestId(context.getRequestId())
                .scene(context.getScene())
                .experimentId(context.getExperimentId())
                .variant(context.getVariant())
                .modelVersion(context.getModelVersion())
                .modelEnabled(context.isModelEnabled())
                .items(feedItems)
                .build();
    }
}
