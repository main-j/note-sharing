package com.project.login.service.recommend.event;

import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.event.model.RecommendEvent;
import com.project.login.service.recommend.model.FeedItemVO;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ExposureLogService {

    private final ExposureEventProducer exposureEventProducer;
    private final StringRedisTemplate redisTemplate;
    private final com.project.login.service.recommend.config.RecommendationFeedProperties feedProperties;
    private final RecommendationInfraProperties infraProperties;

    public void logExposures(RecommendContext context, List<FeedItemVO> feedItems) {
        if (feedItems == null || feedItems.isEmpty()) {
            return;
        }

        String seenKey = infraProperties.getRedis().getSeenItemsKeyPrefix() + context.getUserId();
        for (int i = 0; i < feedItems.size(); i++) {
            FeedItemVO item = feedItems.get(i);
            if (item.getItemType() == null || item.getItemId() == null) {
                continue;
            }

            String itemKey = item.getItemType().name() + ":" + item.getItemId();
            redisTemplate.opsForSet().add(seenKey, itemKey);

            RecommendEvent event = RecommendEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .requestId(context.getRequestId())
                    .userId(context.getUserId())
                    .itemType(item.getItemType())
                    .itemId(item.getItemId())
                    .authorId(item.getAuthorId())
                    .eventType(RecommendEventType.IMPRESSION)
                    .source(item.getReason())
                    .position(i)
                    .scene(context.getScene())
                    .experimentId(context.getExperimentId())
                    .variant(context.getVariant())
                    .modelVersion(context.getModelVersion())
                    .ranker(item.getRanker())
                    .tags(item.getTags() == null ? new ArrayList<>() : item.getTags())
                    .timestamp(System.currentTimeMillis())
                    .build();
            exposureEventProducer.send(event);
        }
        redisTemplate.expire(seenKey, feedProperties.getSeenTtlDays(), TimeUnit.DAYS);
    }
}
