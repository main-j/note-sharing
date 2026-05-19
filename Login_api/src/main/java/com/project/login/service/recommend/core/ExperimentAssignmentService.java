package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.RecommendContext;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperimentAssignmentService {

    private final RolloutStateService rolloutStateService;

    public Assignment assign(Long userId) {
        RolloutStateService.RolloutState state = rolloutStateService.current();
        if (!state.isEnabled() || userId == null) {
            return Assignment.builder()
                    .experimentId(state.getExperimentId())
                    .variant(state.getControlVariant())
                    .modelEnabled(false)
                    .modelVersion(state.getModelVersion())
                    .build();
        }

        long bucket = Math.floorMod(Long.hashCode(userId), 100);
        boolean treatment = bucket < Math.round(state.getModelRankRatio() * 100);
        return Assignment.builder()
                .experimentId(state.getExperimentId())
                .variant(treatment ? state.getTreatmentVariant() : state.getControlVariant())
                .modelEnabled(treatment && state.isModelEnabled())
                .modelVersion(state.getModelVersion())
                .build();
    }

    public void attach(RecommendContext context) {
        Assignment assignment = assign(context.getUserId());
        context.setExperimentId(assignment.getExperimentId());
        context.setVariant(assignment.getVariant());
        context.setModelEnabled(assignment.isModelEnabled());
        context.setModelVersion(assignment.getModelVersion());
        context.getAttributes().put("experimentId", assignment.getExperimentId());
        context.getAttributes().put("variant", assignment.getVariant());
        context.getAttributes().put("modelEnabled", assignment.isModelEnabled());
        context.getAttributes().put("modelVersion", assignment.getModelVersion());
    }

    @Data
    @Builder
    public static class Assignment {
        private String experimentId;
        private String variant;
        private boolean modelEnabled;
        private String modelVersion;
    }
}
