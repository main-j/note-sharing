package com.project.login.service.recommend.core.filter;

import com.project.login.mapper.NoteModerationMapper;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SafetyFilter {

    private final NoteModerationMapper noteModerationMapper;

    public Set<String> blockedNoteKeys() {
        Set<String> blocked = new HashSet<>();
        for (Long noteId : noteModerationMapper.selectPendingFlaggedNoteIds()) {
            blocked.add(ContentCandidate.buildItemKey(ItemType.NOTE, String.valueOf(noteId)));
        }
        return blocked;
    }
}
