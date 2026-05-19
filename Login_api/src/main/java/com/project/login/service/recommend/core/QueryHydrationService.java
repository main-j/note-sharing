package com.project.login.service.recommend.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.mapper.UserFollowMapper;
import com.project.login.model.dataobject.UserFollowDO;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.feature.FeastFeatureClient;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueryHydrationService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserFollowMapper userFollowMapper;
    private final RecommendationInfraProperties infraProperties;
    private final FeastFeatureClient feastFeatureClient;
    private final ExperimentAssignmentService experimentAssignmentService;

    public RecommendContext hydrate(Long userId, int pageSize) {
        return hydrate(userId, pageSize, "HOME", null, null);
    }

    public RecommendContext hydrate(Long userId, int pageSize, String scene, String requestId, List<String> extraSearchTerms) {
        List<String> topTags = loadTopTags(userId);
        List<String> recentSearchTerms = new ArrayList<>(loadRecentSearchTerms(userId));
        if (extraSearchTerms != null) {
            extraSearchTerms.stream()
                    .filter(term -> term != null && !term.isBlank())
                    .filter(term -> !recentSearchTerms.contains(term))
                    .forEach(recentSearchTerms::add);
        }
        List<Long> followeeIds = loadFolloweeIds(userId);
        List<String> recentActions = loadRecentActions(userId);

        RecommendContext context = RecommendContext.builder()
                .userId(userId)
                .pageSize(pageSize)
                .scene(scene == null || scene.isBlank() ? "HOME" : scene)
                .requestId(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId)
                .topTags(topTags)
                .recentSearchTerms(recentSearchTerms)
                .followeeIds(followeeIds)
                .recentItemKeys(recentActions)
                .seenItemKeys(loadSeenItemKeys(userId))
                .build();
        experimentAssignmentService.attach(context);
        return context;
    }

    private List<String> loadTopTags(Long userId) {
        java.util.Map<String, Object> feastUserFeatures = feastFeatureClient.fetchUserFeatures(userId);
        if (!feastUserFeatures.isEmpty()) {
            List<String> tags = new java.util.ArrayList<>();
            Object tag1 = feastUserFeatures.get("top_tag_1");
            Object tag2 = feastUserFeatures.get("top_tag_2");
            if (tag1 != null && !String.valueOf(tag1).isBlank()) {
                tags.add(String.valueOf(tag1));
            }
            if (tag2 != null && !String.valueOf(tag2).isBlank()) {
                tags.add(String.valueOf(tag2));
            }
            if (!tags.isEmpty()) {
                return tags;
            }
        }
        String json = redisTemplate.opsForValue().get(infraProperties.getRedis().getFusedProfileKeyPrefix() + userId);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Double> fused = objectMapper.readValue(json, new TypeReference<>() {
            });
            return fused.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(20)
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    private List<String> loadRecentSearchTerms(Long userId) {
        List<String> terms = redisTemplate.opsForList().range(infraProperties.getRedis().getRecentSearchTermsKeyPrefix() + userId, 0, 19);
        return terms == null ? Collections.emptyList() : terms.stream().filter(s -> !s.isBlank()).toList();
    }

    private List<String> loadRecentActions(Long userId) {
        List<String> actions = redisTemplate.opsForList().range(infraProperties.getRedis().getRecentActionsKeyPrefix() + userId, 0, 49);
        return actions == null ? Collections.emptyList() : actions;
    }

    private java.util.Set<String> loadSeenItemKeys(Long userId) {
        java.util.Set<String> seen = redisTemplate.opsForSet().members(infraProperties.getRedis().getSeenItemsKeyPrefix() + userId);
        return seen == null ? java.util.Collections.emptySet() : seen;
    }

    private List<Long> loadFolloweeIds(Long userId) {
        List<UserFollowDO> follows = userFollowMapper.selectFollowings(userId);
        if (follows == null || follows.isEmpty()) {
            return Collections.emptyList();
        }
        return follows.stream()
                .map(UserFollowDO::getFolloweeId)
                .distinct()
                .collect(Collectors.toList());
    }
}
