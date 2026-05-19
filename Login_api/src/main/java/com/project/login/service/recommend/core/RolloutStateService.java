package com.project.login.service.recommend.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.service.recommend.config.RecommendationExperimentProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class RolloutStateService {

    private final RecommendationExperimentProperties experimentProperties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedState> cached = new AtomicReference<>();

    public RolloutState current() {
        Path statePath = Path.of(experimentProperties.getStateFile());
        try {
            if (!Files.exists(statePath)) {
                return defaultState();
            }
            long modifiedAt = Files.getLastModifiedTime(statePath).toMillis();
            CachedState snapshot = cached.get();
            if (snapshot != null && snapshot.modifiedAt == modifiedAt) {
                return snapshot.state;
            }
            RolloutState loaded = objectMapper.readValue(statePath.toFile(), RolloutState.class);
            if (loaded.getExperimentId() == null || loaded.getExperimentId().isBlank()) {
                loaded.setExperimentId(experimentProperties.getExperimentId());
            }
            if (loaded.getControlVariant() == null || loaded.getControlVariant().isBlank()) {
                loaded.setControlVariant(experimentProperties.getControlVariant());
            }
            if (loaded.getTreatmentVariant() == null || loaded.getTreatmentVariant().isBlank()) {
                loaded.setTreatmentVariant(experimentProperties.getTreatmentVariant());
            }
            if (loaded.getModelVersion() == null || loaded.getModelVersion().isBlank()) {
                loaded.setModelVersion(experimentProperties.getDefaultModelVersion());
            }
            cached.set(new CachedState(modifiedAt, loaded));
            return loaded;
        } catch (Exception ignore) {
            return defaultState();
        }
    }

    private RolloutState defaultState() {
        return RolloutState.builder()
                .enabled(experimentProperties.isEnabled())
                .experimentId(experimentProperties.getExperimentId())
                .controlVariant(experimentProperties.getControlVariant())
                .treatmentVariant(experimentProperties.getTreatmentVariant())
                .modelRankRatio(experimentProperties.getModelRankRatio())
                .modelEnabled(experimentProperties.getModelRankRatio() > 0)
                .modelVersion(experimentProperties.getDefaultModelVersion())
                .updatedAt(Instant.now().toString())
                .build();
    }

    private record CachedState(long modifiedAt, RolloutState state) {
    }

    @Data
    @Builder
    public static class RolloutState {
        private boolean enabled;
        private String experimentId;
        private String controlVariant;
        private String treatmentVariant;
        private double modelRankRatio;
        private boolean modelEnabled;
        private String modelVersion;
        private String previousModelVersion;
        private String updatedAt;
    }
}
