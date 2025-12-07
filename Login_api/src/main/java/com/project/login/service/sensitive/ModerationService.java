package com.project.login.service.sensitive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.mapper.NoteModerationMapper;
import com.project.login.model.dataobject.NoteModerationDO;
import com.project.login.model.vo.SensitiveCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final NoteModerationMapper noteModerationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void saveResult(SensitiveCheckResult r) {
        NoteModerationDO d = new NoteModerationDO();
        if (r.getNoteMeta() != null) d.setNoteId(r.getNoteMeta().getNoteId());
        d.setStatus(r.getStatus());
        d.setRiskLevel(r.getRiskLevel());
        d.setScore(r.getScore() == null ? null : r.getScore().intValue());
        try {
            d.setCategoriesJson(objectMapper.writeValueAsString(r.getCategories()));
            d.setFindingsJson(objectMapper.writeValueAsString(r.getFindings()));
        } catch (Exception e) {
            d.setCategoriesJson("[]");
            d.setFindingsJson("[]");
        }
        d.setSource("LLM");
        d.setCreatedAt(LocalDateTime.now());
        d.setIsHandled(Boolean.FALSE);
        noteModerationMapper.insert(d);
    }
}
