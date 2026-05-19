package com.project.login.service.recommend.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FeastFeatureClient {

    private final RecommendationInfraProperties infraProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public Map<String, Object> fetchUserFeatures(Long userId) {
        if (!infraProperties.getFeast().isEnabled() || userId == null) {
            return Map.of();
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "features", List.of(
                            "user_features:top_tag_1",
                            "user_features:top_tag_2",
                            "user_features:recent_search_count"
                    ),
                    "entities", Map.of("user_id", List.of(userId))
            ));
            JsonNode response = callOnlineFeatures(body);
            return parseRow(response, 0);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public void enrich(RecommendContext context, List<ContentCandidate> candidates) {
        if (!infraProperties.getFeast().isEnabled()) {
            return;
        }
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (ContentCandidate candidate : candidates) {
            if (candidate.getItemType() == null || candidate.getItemId() == null) {
                continue;
            }
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "features", List.of(
                                "item_features:views",
                                "item_features:likes",
                                "item_features:favorites",
                                "item_features:comments",
                                "item_features:hot_score"
                        ),
                        "entities", Map.of("item_id", List.of(candidate.getItemId()))
                ));
                JsonNode response = callOnlineFeatures(body);
                Map<String, Object> row = parseRow(response, 0);
                row.forEach((key, value) -> candidate.getFeatures().putIfAbsent(key, value));
            } catch (Exception ignored) {
                // degrade to redis/local features
            }
        }
    }

    private JsonNode callOnlineFeatures(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(infraProperties.getFeast().getServerUrl() + "/get-online-features"))
                .timeout(Duration.ofMillis(infraProperties.getFeast().getTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("feast feature server call failed");
        }
        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> parseRow(JsonNode root, int index) {
        JsonNode metadata = root.path("metadata").path("feature_names");
        JsonNode results = root.path("results");
        if (!metadata.isArray() || !results.isArray()) {
            return Map.of();
        }
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < metadata.size() && i < results.size(); i++) {
            String rawName = metadata.get(i).asText();
            String mappedName = rawName.contains(":") ? rawName.substring(rawName.indexOf(':') + 1) : rawName;
            JsonNode values = results.get(i).path("values");
            if (values.isArray() && index < values.size()) {
                JsonNode value = values.get(index);
                if (!value.isNull()) {
                    if (value.isNumber()) {
                        row.put(mappedName, value.numberValue());
                    } else if (value.isBoolean()) {
                        row.put(mappedName, value.booleanValue());
                    } else {
                        row.put(mappedName, value.asText());
                    }
                }
            }
        }
        return row;
    }
}
