package com.project.login.service.recommend.core.filter;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import org.springframework.stereotype.Component;

@Component
public class SeenFilter {

    public boolean allow(RecommendContext context, ContentCandidate candidate) {
        if (candidate.getItemKey() == null) {
            return false;
        }
        // Search should return all ES matches; seen history is for feed dedup only.
        if ("SEARCH".equalsIgnoreCase(context.getScene())) {
            return true;
        }
        var seen = context.getSeenItemKeys();
        if (seen == null || seen.isEmpty()) {
            return true;
        }
        int pageSize = context.getPageSize() == null ? 20 : context.getPageSize();
        // Small catalog: once most items are marked seen, stop hard-filtering and demote via ranking instead.
        if (seen.size() > pageSize * 8) {
            return true;
        }
        return !seen.contains(candidate.getItemKey());
    }
}
