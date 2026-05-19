package com.project.login.service.recommend.core.filter;

import com.project.login.service.recommend.model.ContentCandidate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuthorFilter {

    public boolean allow(Map<Long, Integer> authorCount, ContentCandidate candidate, int maxPerAuthor) {
        if (candidate.getAuthorId() == null) {
            return true;
        }
        int count = authorCount.getOrDefault(candidate.getAuthorId(), 0);
        if (count >= maxPerAuthor) {
            return false;
        }
        authorCount.put(candidate.getAuthorId(), count + 1);
        return true;
    }

    public Map<Long, Integer> newCounter() {
        return new HashMap<>();
    }
}
