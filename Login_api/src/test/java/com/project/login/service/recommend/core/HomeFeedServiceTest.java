package com.project.login.service.recommend.core;

import com.project.login.service.recommend.event.ExposureLogService;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.FeedItemVO;
import com.project.login.service.recommend.model.HomeFeedResult;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeFeedServiceTest {

    @Mock
    private QueryHydrationService queryHydrationService;
    @Mock
    private RecallOrchestrator recallOrchestrator;
    @Mock
    private CandidateMergeService candidateMergeService;
    @Mock
    private CandidateFilterService candidateFilterService;
    @Mock
    private FeatureHydrationService featureHydrationService;
    @Mock
    private RankService rankService;
    @Mock
    private RerankService rerankService;
    @Mock
    private FeedSelectionService feedSelectionService;
    @Mock
    private ExposureLogService exposureLogService;

    @InjectMocks
    private HomeFeedService homeFeedService;

    @Test
    void recommend_shouldReturnRequestIdAndLogExposures() {
        RecommendContext context = RecommendContext.builder()
                .userId(1L)
                .pageSize(5)
                .requestId("req-abc")
                .build();
        ContentCandidate candidate = ContentCandidate.builder()
                .itemType(ItemType.NOTE)
                .itemId("1")
                .itemKey("NOTE:1")
                .build();
        FeedItemVO feedItem = FeedItemVO.builder().itemType(ItemType.NOTE).itemId("1").build();

        when(queryHydrationService.hydrate(1L, 5)).thenReturn(context);
        when(recallOrchestrator.recall(context)).thenReturn(List.of(candidate));
        when(candidateMergeService.merge(any())).thenReturn(List.of(candidate));
        when(candidateFilterService.filter(eq(context), any())).thenReturn(List.of(candidate));
        when(featureHydrationService.hydrate(eq(context), any())).thenReturn(List.of(candidate));
        when(rankService.rank(eq(context), any())).thenReturn(List.of(candidate));
        when(rerankService.rerank(eq(context), any())).thenReturn(List.of(candidate));
        when(feedSelectionService.select(eq(context), any())).thenReturn(List.of(feedItem));

        HomeFeedResult result = homeFeedService.recommend(1L, 5);

        assertThat(result.getRequestId()).isEqualTo("req-abc");
        assertThat(result.getItems()).hasSize(1);
        verify(exposureLogService).logExposures(context, List.of(feedItem));
    }
}
