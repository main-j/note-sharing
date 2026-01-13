package com.project.login.service.sensitive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.mapper.NoteModerationMapper;
import com.project.login.mapper.NoteMapper;
import com.project.login.model.dataobject.NoteModerationDO;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.model.vo.NoteModerationVO;
import com.project.login.model.vo.SensitiveCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final NoteModerationMapper noteModerationMapper;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void saveResult(SensitiveCheckResult r) {
        Long noteId = r.getNoteMeta() == null ? null : r.getNoteMeta().getNoteId();
        NoteModerationDO existing = noteId == null ? null : noteModerationMapper.selectLatestPendingByNoteId(noteId);

        NoteModerationDO d = existing == null ? new NoteModerationDO() : existing;
        if (noteId != null) d.setNoteId(noteId);
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

        if (existing == null) {
            noteModerationMapper.insert(d);
        } else {
            noteModerationMapper.updateFields(d);
        }
    }

    @Transactional(readOnly = true)
    public List<NoteModerationVO> getPendingFlagged() {
        List<NoteModerationDO> doList = noteModerationMapper.selectPendingFlagged();
        return convertToVOList(doList);
    }

    @Transactional(readOnly = true)
    public NoteModerationVO getById(Long id) {
        NoteModerationDO moderationDO = noteModerationMapper.selectById(id);
        if (moderationDO == null) {
            return null;
        }
        return convertToVO(moderationDO);
    }

    @Transactional(readOnly = true)
    public List<NoteModerationVO> getByNoteId(Long noteId) {
        List<NoteModerationDO> doList = noteModerationMapper.selectByNoteId(noteId);
        return convertToVOList(doList);
    }

    @Transactional
    public void handleModeration(Long id, Boolean isHandled, String adminComment) {
        NoteModerationDO moderationDO = noteModerationMapper.selectById(id);
        if (moderationDO == null) {
            throw new RuntimeException("审查记录不存在");
        }
        moderationDO.setIsHandled(isHandled);
        moderationDO.setAdminComment(adminComment);
        noteModerationMapper.updateHandled(moderationDO);
    }

    private List<NoteModerationVO> convertToVOList(List<NoteModerationDO> doList) {
        if (doList == null || doList.isEmpty()) {
            return new ArrayList<>();
        }
        return doList.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    private NoteModerationVO convertToVO(NoteModerationDO doObj) {
        NoteModerationVO.NoteModerationVOBuilder builder = NoteModerationVO.builder()
                .id(doObj.getId())
                .noteId(doObj.getNoteId())
                .status(doObj.getStatus())
                .riskLevel(doObj.getRiskLevel())
                .score(doObj.getScore())
                .source(doObj.getSource())
                .createdAt(doObj.getCreatedAt())
                .isHandled(doObj.getIsHandled())
                .adminComment(doObj.getAdminComment());

        // 获取笔记标题
        if (doObj.getNoteId() != null) {
            try {
                NoteDO note = noteMapper.selectById(doObj.getNoteId());
                if (note != null) {
                    builder.noteTitle(note.getTitle());
                } else {
                    builder.noteTitle("笔记已删除");
                }
            } catch (Exception e) {
                builder.noteTitle("未知笔记");
            }
        }

        // 解析 JSON 字段
        try {
            if (doObj.getCategoriesJson() != null && !doObj.getCategoriesJson().isEmpty()) {
                List<String> categories = objectMapper.readValue(doObj.getCategoriesJson(), 
                        new TypeReference<List<String>>() {});
                builder.categories(categories);
            } else {
                builder.categories(new ArrayList<>());
            }
        } catch (Exception e) {
            builder.categories(new ArrayList<>());
        }

        try {
            if (doObj.getFindingsJson() != null && !doObj.getFindingsJson().isEmpty()) {
                List<Object> findings = objectMapper.readValue(doObj.getFindingsJson(), 
                        new TypeReference<List<Object>>() {});
                builder.findings(findings);
            } else {
                builder.findings(new ArrayList<>());
            }
        } catch (Exception e) {
            builder.findings(new ArrayList<>());
        }

        return builder.build();
    }
}
