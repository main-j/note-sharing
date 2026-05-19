package com.project.login.service.recommend.rank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Order(90)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation.infra.triton", name = "enabled", havingValue = "true")
public class TritonRanker implements Ranker {

    private final RecommendationInfraProperties infraProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Override
    public List<ContentCandidate> rank(RecommendContext context, List<ContentCandidate> candidates) {
        for (ContentCandidate candidate : candidates) {
            try {
                float[] row = new float[]{
                        feature(candidate, "views"),
                        feature(candidate, "likes"),
                        feature(candidate, "favorites"),
                        feature(candidate, "comments"),
                        feature(candidate, "answers"),
                        feature(candidate, "tagMatchScore"),
                        (float) candidate.getRecallScore(),
                };
                float score = infer(row);
                candidate.getFeatures().put("tritonRankScore", score);
                candidate.setRankScore(score);
            } catch (Exception ignored) {
                // fall back to other rankers
            }
        }
        return candidates;
    }

    private float infer(float[] row) throws Exception {
        String url = infraProperties.getTriton().getEndpoint()
                + "/v2/models/" + infraProperties.getTriton().getModelName() + "/infer";
        String body = objectMapper.writeValueAsString(Map.of(
                "inputs", List.of(Map.of(
                        "name", "input",
                        "shape", List.of(1, row.length),
                        "datatype", "FP32",
                        "data", toList(row)
                ))
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(infraProperties.getTriton().getTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("triton infer request failed");
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode outputs = root.path("outputs");
        if (!outputs.isArray() || outputs.isEmpty()) {
            throw new IllegalStateException("triton infer response has no outputs");
        }
        JsonNode data = outputs.get(0).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("triton infer response has no output data");
        }
        return (float) data.get(0).asDouble(0.0);
    }

    private List<Float> toList(float[] values) {
        List<Float> result = new java.util.ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private float feature(ContentCandidate candidate, String key) {
        Object value = candidate.getFeatures().get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return 0F;
    }
}
