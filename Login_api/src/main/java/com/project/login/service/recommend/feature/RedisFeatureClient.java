package com.project.login.service.recommend.feature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisFeatureClient {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationInfraProperties infraProperties;

    public void mergeRealtimeStats(ContentCandidate candidate) {
        if (candidate.getItemType() == null || candidate.getItemId() == null) {
            return;
        }
        String key = infraProperties.getRedis().getItemRealtimeStatsKeyPrefix()
                + candidate.getItemType().name() + ":" + candidate.getItemId();
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            Map<String, Object> stats = objectMapper.readValue(json, new TypeReference<>() {});
            candidate.getFeatures().putAll(stats);
        } catch (Exception ignored) {
            // ignore malformed redis payload
        }
    }
}
