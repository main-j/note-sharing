package com.project.login.service.recommend.core.filter;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import org.springframework.stereotype.Component;

@Component
public class SeenFilter {

    public boolean allow(RecommendContext context, ContentCandidate candidate) {
        return candidate.getItemKey() != null && !context.getSeenItemKeys().contains(candidate.getItemKey());
    }
}
