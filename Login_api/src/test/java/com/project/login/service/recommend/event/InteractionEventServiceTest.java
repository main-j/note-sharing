package com.project.login.service.recommend.event;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.event.model.InteractionEventRequest;
import com.project.login.service.recommend.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionEventServiceTest {

    @Mock
    private BehaviorEventProducer behaviorEventProducer;
    @Mock
    private SearchEventProducer searchEventProducer;
    @Mock
    private ContentEventProducer contentEventProducer;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RecommendationFeedProperties feedProperties;
    @Mock
    private RecommendationInfraProperties infraProperties;
    @Mock
    private ListOperations<String, String> listOperations;
    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private InteractionEventService interactionEventService;

    @Test
    void recordInteraction_shouldDispatchSearchEvent() {
        RecommendationInfraProperties.Redis redis = new RecommendationInfraProperties.Redis();
        when(infraProperties.getRedis()).thenReturn(redis);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        InteractionEventRequest request = new InteractionEventRequest();
        request.setUserId(1L);
        request.setEventType(RecommendEventType.SEARCH);
        request.setKeyword("java");

        interactionEventService.recordInteraction(request);

        verify(searchEventProducer).send(any());
    }

    @Test
    void recordInteraction_shouldDispatchBehaviorEventForClick() {
        RecommendationInfraProperties.Redis redis = new RecommendationInfraProperties.Redis();
        when(infraProperties.getRedis()).thenReturn(redis);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        InteractionEventRequest request = new InteractionEventRequest();
        request.setUserId(1L);
        request.setEventType(RecommendEventType.CLICK);
        request.setItemType(ItemType.NOTE);
        request.setItemId("10");

        interactionEventService.recordInteraction(request);

        verify(behaviorEventProducer).send(any());
    }
}
