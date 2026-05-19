package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CandidateMergeService {

    public List<ContentCandidate> merge(List<ContentCandidate> candidates) {
        Map<String, ContentCandidate> merged = new LinkedHashMap<>();

        for (ContentCandidate candidate : candidates) {
            if (candidate == null || candidate.getItemKey() == null) {
                continue;
            }

            ContentCandidate existing = merged.get(candidate.getItemKey());
            if (existing == null) {
                merged.put(candidate.getItemKey(), candidate);
                continue;
            }

            existing.setRecallScore(Math.max(existing.getRecallScore(), candidate.getRecallScore()));
            if (candidate.getSource() != null && !candidate.getSource().isBlank()) {
                existing.setSource(mergeSource(existing.getSource(), candidate.getSource()));
            }
            if (candidate.getFeatures() != null && !candidate.getFeatures().isEmpty()) {
                Map<String, Object> mergedFeatures = new HashMap<>(existing.getFeatures());
                mergedFeatures.putAll(candidate.getFeatures());
                existing.setFeatures(mergedFeatures);
            }
        }

        return new ArrayList<>(merged.values());
    }

    private String mergeSource(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        if (left.contains(right)) {
            return left;
        }
        return left + "," + right;
    }
}
