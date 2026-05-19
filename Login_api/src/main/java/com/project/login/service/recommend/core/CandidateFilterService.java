package com.project.login.service.recommend.core;

import com.project.login.service.recommend.core.filter.AuthorFilter;
import com.project.login.service.recommend.core.filter.SafetyFilter;
import com.project.login.service.recommend.core.filter.SeenFilter;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CandidateFilterService {

    private final SeenFilter seenFilter;
    private final SafetyFilter safetyFilter;
    private final AuthorFilter authorFilter;

    public List<ContentCandidate> filter(RecommendContext context, List<ContentCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> blockedNoteKeys = safetyFilter.blockedNoteKeys();
        Map<Long, Integer> authorCounter = authorFilter.newCounter();

        return candidates.stream()
                .filter(candidate -> candidate.getItemKey() != null)
                .filter(candidate -> seenFilter.allow(context, candidate))
                .filter(candidate -> !blockedNoteKeys.contains(candidate.getItemKey()))
                .filter(candidate -> authorFilter.allow(authorCounter, candidate, 3))
                .toList();
    }
}
