package com.project.login.service.recommend.mlops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.service.recommend.config.RecommendationMlopsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendMlopsClient {

    private final RecommendationMlopsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public Map<String, Object> triggerTraining(boolean runExport) {
        try {
            Map<String, Object> payload = Map.of(
                    "modelName", properties.getModelName(),
                    "modelType", properties.getModelType(),
                    "runExport", runExport
            );
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getServiceUrl() + "/internal/recommend/train"))
                    .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Map.of("accepted", false, "statusCode", response.statusCode(), "body", response.body());
            }
            return objectMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of("accepted", false, "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    public Map<String, Object> status() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getServiceUrl() + "/internal/recommend/status"))
                    .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Map.of("available", false, "statusCode", response.statusCode(), "body", response.body());
            }
            return objectMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of("available", false, "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }
}
