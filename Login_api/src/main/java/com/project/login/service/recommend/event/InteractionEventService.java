package com.project.login.service.recommend.event;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.event.model.InteractionEventRequest;
import com.project.login.service.recommend.event.model.RecommendEvent;
import com.project.login.service.recommend.model.ContentCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InteractionEventService {

    private final BehaviorEventProducer behaviorEventProducer;
    private final SearchEventProducer searchEventProducer;
    private final ContentEventProducer contentEventProducer;
    private final StringRedisTemplate redisTemplate;
    private final RecommendationFeedProperties feedProperties;
    private final RecommendationInfraProperties infraProperties;

    public void recordInteraction(InteractionEventRequest request) {
        RecommendEvent event = RecommendEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .itemType(request.getItemType())
                .itemId(request.getItemId())
                .authorId(request.getAuthorId())
                .eventType(request.getEventType())
                .source(request.getSource())
                .position(request.getPosition())
                .scene(request.getScene())
                .experimentId(request.getExperimentId())
                .variant(request.getVariant())
                .modelVersion(request.getModelVersion())
                .ranker(request.getRanker())
                .tags(request.getTags() == null ? new ArrayList<>() : request.getTags())
                .timestamp(System.currentTimeMillis())
                .keyword(request.getKeyword())
                .build();

        dispatch(event);

        maintainRedisState(event);
    }

    private void dispatch(RecommendEvent event) {
        switch (event.getEventType()) {
            case SEARCH -> searchEventProducer.send(event);
            case FOLLOW -> contentEventProducer.send(event);
            default -> behaviorEventProducer.send(event);
        }
    }

    private void maintainRedisState(RecommendEvent event) {
        Long userId = event.getUserId();
        if (userId == null) {
            return;
        }

        if (event.getEventType() == RecommendEventType.SEARCH && event.getKeyword() != null && !event.getKeyword().isBlank()) {
            String key = infraProperties.getRedis().getRecentSearchTermsKeyPrefix() + userId;
            redisTemplate.opsForList().leftPush(key, event.getKeyword());
            redisTemplate.opsForList().trim(key, 0, feedProperties.getRecentActionSize() - 1L);
            redisTemplate.expire(key, feedProperties.getSeenTtlDays(), TimeUnit.DAYS);
            return;
        }

        if (event.getItemType() == null || event.getItemId() == null || event.getItemId().isBlank()) {
            return;
        }

        String itemKey = ContentCandidate.buildItemKey(event.getItemType(), event.getItemId());
        String seenKey = infraProperties.getRedis().getSeenItemsKeyPrefix() + userId;
        redisTemplate.opsForSet().add(seenKey, itemKey);
        redisTemplate.expire(seenKey, feedProperties.getSeenTtlDays(), TimeUnit.DAYS);

        if (event.getEventType() == RecommendEventType.IMPRESSION) {
            return;
        }

        String actionKey = infraProperties.getRedis().getRecentActionsKeyPrefix() + userId;
        redisTemplate.opsForList().leftPush(actionKey, event.getEventType() + "|" + itemKey);
        redisTemplate.opsForList().trim(actionKey, 0, feedProperties.getRecentActionSize() - 1L);
        redisTemplate.expire(actionKey, feedProperties.getSeenTtlDays(), TimeUnit.DAYS);
    }
}
