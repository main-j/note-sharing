package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import com.project.login.service.recommend.recall.RecallSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecallOrchestrator {

    private final List<RecallSource> recallSources;

    public List<ContentCandidate> recall(RecommendContext context) {
        List<ContentCandidate> candidates = new ArrayList<>();
        for (RecallSource recallSource : recallSources) {
            List<ContentCandidate> recalled = recallSource.recall(context);
            if (recalled != null) {
                candidates.addAll(recalled);
            }
        }
        return candidates;
    }
}
