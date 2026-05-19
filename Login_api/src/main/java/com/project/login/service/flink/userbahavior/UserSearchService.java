package com.project.login.service.flink.userbahavior;

import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.event.SearchEventProducer;
import com.project.login.service.recommend.event.RecommendEventType;
import com.project.login.service.recommend.event.model.RecommendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchService {

    private final StringRedisTemplate redisTemplate;
    private final SearchEventProducer searchEventProducer;
    private final RecommendationInfraProperties infraProperties;

    private static final String STREAM_KEY = "user_search_stream";

    public void recordSearch(Long userId, String keyword) {

        if (userId == null || keyword == null || keyword.isBlank()) {
            return;
        }

        Map<String, String> map = new HashMap<>();
        map.put("user_id", String.valueOf(userId));
        map.put("keyword", keyword);
        map.put("timestamp", String.valueOf(System.currentTimeMillis()));

        if (infraProperties.getEvent().isLegacyRedisStreamEnabled()) {
            redisTemplate.opsForStream().add(STREAM_KEY, map);
        }

        long ts = System.currentTimeMillis();
        RecommendEvent event = RecommendEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .eventType(RecommendEventType.SEARCH)
                .keyword(keyword)
                .timestamp(ts)
                .build();
        searchEventProducer.send(event);

        log.info("Recorded user search for Flink/Kafka: userId={}, keyword={}", userId, keyword);
    }
}

