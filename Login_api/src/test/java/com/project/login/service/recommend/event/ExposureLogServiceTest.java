package com.project.login.service.recommend.event;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.FeedItemVO;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExposureLogServiceTest {

    @Mock
    private ExposureEventProducer exposureEventProducer;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RecommendationFeedProperties feedProperties;
    @Mock
    private RecommendationInfraProperties infraProperties;
    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private ExposureLogService exposureLogService;

    @Test
    void logExposures_shouldPublishExposureEvents() {
        RecommendationInfraProperties.Redis redis = new RecommendationInfraProperties.Redis();
        when(infraProperties.getRedis()).thenReturn(redis);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        RecommendContext context = RecommendContext.builder()
                .userId(1L)
                .requestId("req-1")
                .build();
        FeedItemVO item = FeedItemVO.builder()
                .itemType(ItemType.NOTE)
                .itemId("100")
                .reason("HOT")
                .build();

        exposureLogService.logExposures(context, List.of(item));

        verify(exposureEventProducer).send(any());
    }
}
