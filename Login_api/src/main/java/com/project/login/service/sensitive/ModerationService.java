package com.project.login.service.sensitive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.mapper.NoteModerationMapper;
import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NotebookMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.model.dataobject.NoteModerationDO;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.service.noting.NoteService;
import com.project.login.service.notification.NotificationService;
import com.project.login.mapper.UserMapper;
import com.project.login.model.entity.UserEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import com.project.login.model.vo.NoteModerationVO;
import com.project.login.model.vo.NoteReviewVO;
import com.project.login.model.vo.PendingNoteVO;
import com.project.login.model.vo.SensitiveCheckResult;
import com.project.login.service.minio.MinioService;
import com.project.login.service.sensitive.DeepFilterService;
import com.project.login.service.noting.ContentSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final NoteModerationMapper noteModerationMapper;
    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper noteSpaceMapper;
    private final MinioService minioService;
    private final DeepFilterService deepFilterService;
    private final ContentSummaryService contentSummaryService;
    private final NoteService noteService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
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

    /**
     * 保存审查结果并返回审查记录DO对象（包含ID和创建时间）
     * @param r 敏感词检查结果
     * @return 保存后的审查记录DO对象
     */
    public NoteModerationDO saveResultAndReturnDO(SensitiveCheckResult r) {
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
        LocalDateTime now = LocalDateTime.now();
        d.setCreatedAt(now);
        d.setIsHandled(Boolean.FALSE);

        if (existing == null) {
            noteModerationMapper.insert(d);
        } else {
            noteModerationMapper.updateFields(d);
        }
        
        return d;
    }

    @Transactional(readOnly = true)
    public List<NoteModerationVO> getPendingFlagged() {
        List<NoteModerationDO> doList = noteModerationMapper.selectPendingFlagged();
        return convertToVOList(doList);
    }

    /**
     * 获取所有审查记录（包括已处理和未处理的），用于留档
     * @return 所有审查记录列表
     */
    @Transactional(readOnly = true)
    public List<NoteModerationVO> getAllFlagged() {
        List<NoteModerationDO> doList = noteModerationMapper.selectAllFlagged();
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

    @Transactional(readOnly = true)
    public List<PendingNoteVO> getPendingNotes() {
        // 获取所有待处理的审查记录
        List<NoteModerationDO> moderationList = noteModerationMapper.selectPendingFlagged();
        
        List<PendingNoteVO> result = new ArrayList<>();
        
        for (NoteModerationDO moderation : moderationList) {
            if (moderation.getNoteId() == null) {
                continue;
            }
            
            try {
                // 获取笔记信息
                NoteDO note = noteMapper.selectById(moderation.getNoteId());
                if (note == null) {
                    continue; // 笔记已删除，跳过
                }
                
                // 构建 PendingNoteVO
                PendingNoteVO.PendingNoteVOBuilder builder = PendingNoteVO.builder()
                        .noteId(note.getId())
                        .noteTitle(note.getTitle())
                        .fileType(note.getFileType())
                        .filename(note.getFilename())
                        .notebookId(note.getNotebookId())
                        .noteCreatedAt(note.getCreatedAt())
                        .noteUpdatedAt(note.getUpdatedAt())
                        .moderationId(moderation.getId())
                        .status(moderation.getStatus())
                        .riskLevel(moderation.getRiskLevel())
                        .score(moderation.getScore())
                        .source(moderation.getSource())
                        .moderationCreatedAt(moderation.getCreatedAt())
                        .isHandled(moderation.getIsHandled())
                        .adminComment(moderation.getAdminComment());
                
                // 获取文件URL
                if (note.getFilename() != null && !note.getFilename().isEmpty()) {
                    try {
                        String fileUrl = minioService.getFileUrl(note.getFilename());
                        builder.fileUrl(fileUrl);
                    } catch (Exception e) {
                        builder.fileUrl(null);
                    }
                }
                
                // 解析 categories JSON
                try {
                    if (moderation.getCategoriesJson() != null && !moderation.getCategoriesJson().isEmpty()) {
                        List<String> categories = objectMapper.readValue(moderation.getCategoriesJson(), 
                                new TypeReference<List<String>>() {});
                        builder.categories(categories);
                    } else {
                        builder.categories(new ArrayList<>());
                    }
                } catch (Exception e) {
                    builder.categories(new ArrayList<>());
                }
                
                // 解析 findings JSON
                try {
                    if (moderation.getFindingsJson() != null && !moderation.getFindingsJson().isEmpty()) {
                        List<Object> findings = objectMapper.readValue(moderation.getFindingsJson(), 
                                new TypeReference<List<Object>>() {});
                        builder.findings(findings);
                    } else {
                        builder.findings(new ArrayList<>());
                    }
                } catch (Exception e) {
                    builder.findings(new ArrayList<>());
                }
                
                result.add(builder.build());
            } catch (Exception e) {
                // 记录错误但继续处理其他笔记
                continue;
            }
        }
        
        return result;
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

    /**
     * 审查笔记（调用深度检测）
     * @param noteId 笔记ID
     * @return 审查结果，包含笔记内容和深度检测结果
     */
    @Transactional(readOnly = true)
    public NoteReviewVO reviewNote(Long noteId) {
        NoteDO note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new RuntimeException("笔记不存在");
        }

        // 获取笔记文本内容
        String noteContent = "";
        try {
            byte[] bytes = minioService.download(note.getFilename());
            noteContent = contentSummaryService.extractFullText(bytes, note.getFilename());
        } catch (Exception e) {
            throw new RuntimeException("无法获取笔记内容: " + e.getMessage());
        }

        // 调用深度检测
        Map<String, Object> deepCheckResult = deepFilterService.deepCheck(noteContent);

        // 构建返回结果
        NoteReviewVO.NoteReviewVOBuilder builder = NoteReviewVO.builder()
                .noteId(noteId)
                .noteTitle(note.getTitle())
                .noteContent(noteContent)
                .fileType(note.getFileType())
                .hasSensitiveWords((Boolean) deepCheckResult.getOrDefault("hasSensitiveWords", false))
                .hitCount((Integer) deepCheckResult.getOrDefault("hitCount", 0));

        // 处理 uniqueHits
        Object uniqueHitsObj = deepCheckResult.get("uniqueHits");
        if (uniqueHitsObj instanceof List) {
            builder.uniqueHits((List<String>) uniqueHitsObj);
        } else if (uniqueHitsObj instanceof java.util.Set) {
            builder.uniqueHits(new ArrayList<>((java.util.Set<String>) uniqueHitsObj));
        } else {
            builder.uniqueHits(new ArrayList<>());
        }

        // 处理 findings
        Object findingsObj = deepCheckResult.get("findings");
        if (findingsObj instanceof List) {
            builder.findings((List<Map<String, Object>>) findingsObj);
        } else {
            builder.findings(new ArrayList<>());
        }

        return builder.build();
    }

    /**
     * 处理审查结果（通过/未通过，发布/退回，发送通知）
     * @param moderationId 审查记录ID
     * @param approved true=通过，false=未通过
     * @param adminComment 管理员备注
     */
    @Transactional
    public void handleReviewResult(Long moderationId, Boolean approved, String adminComment) {
        // 获取审查记录
        NoteModerationDO moderation = noteModerationMapper.selectById(moderationId);
        if (moderation == null) {
            throw new RuntimeException("审查记录不存在");
        }

        Long noteId = moderation.getNoteId();
        if (noteId == null) {
            throw new RuntimeException("笔记ID不存在");
        }

        // 获取笔记信息
        NoteDO note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new RuntimeException("笔记不存在");
        }

        // 获取笔记作者ID
        Long authorId = getNoteAuthorId(noteId);
        if (authorId == null) {
            throw new RuntimeException("无法获取笔记作者ID");
        }

        // 更新审查记录
        moderation.setIsHandled(true);
        moderation.setAdminComment(adminComment);
        noteModerationMapper.updateHandled(moderation);

        // 获取管理员用户ID
        Long adminId = getAdminUserId();
        if (adminId == null) {
            log.warn("无法获取管理员用户ID，跳过发送通知");
            return;
        }
        
        if (approved) {
            // 通过：发布笔记
            try {
                // 获取笔记文件
                byte[] fileBytes = minioService.download(note.getFilename());
                String originalFilename = note.getFilename();
                
                // 创建MultipartFile对象
                MultipartFile file = new MultipartFile() {
                    @Override
                    public String getName() {
                        return "file";
                    }

                    @Override
                    public String getOriginalFilename() {
                        return originalFilename;
                    }

                    @Override
                    public String getContentType() {
                        return "application/octet-stream";
                    }

                    @Override
                    public boolean isEmpty() {
                        return fileBytes.length == 0;
                    }

                    @Override
                    public long getSize() {
                        return fileBytes.length;
                    }

                    @Override
                    public byte[] getBytes() throws IOException {
                        return fileBytes;
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(fileBytes);
                    }

                    @Override
                    public void transferTo(File dest) throws IOException, IllegalStateException {
                        throw new UnsupportedOperationException("transferTo not supported");
                    }
                };

                // 构造发布DTO
                com.project.login.model.dto.note.NotePublishDTO publishDTO = 
                    new com.project.login.model.dto.note.NotePublishDTO();
                com.project.login.model.request.note.NoteUpdateMeta meta = 
                    new com.project.login.model.request.note.NoteUpdateMeta();
                meta.setId(noteId);
                meta.setTitle(note.getTitle());
                meta.setNotebookId(note.getNotebookId());
                meta.setFileType(note.getFileType());
                publishDTO.setMeta(meta);
                publishDTO.setFile(file);

                // 发布笔记
                noteService.publishNote(publishDTO);
                
                // 发送审查通过通知
                try {
                    notificationService.createNoteModerationApprovedNotification(adminId, noteId, note.getTitle());
                } catch (Exception e) {
                    log.error("发送审查通过通知失败", e);
                    // 通知发送失败不影响审查结果
                }
            } catch (Exception e) {
                log.error("发布笔记失败", e);
                // 即使发布失败，也发送通知告知用户审查通过但发布出错
                try {
                    notificationService.createNoteModerationApprovedNotification(adminId, noteId, note.getTitle());
                } catch (Exception ex) {
                    log.error("发送审查通知失败", ex);
                }
            }
        } else {
            // 未通过：退回，发送审查未通过通知
            String reason = adminComment != null && !adminComment.trim().isEmpty() 
                ? adminComment 
                : "内容不符合平台规范";
            try {
                notificationService.createNoteModerationRejectedNotification(adminId, noteId, note.getTitle(), reason);
            } catch (Exception e) {
                log.error("发送审查未通过通知失败", e);
                // 通知发送失败不影响审查结果
            }
        }
    }

    /**
     * 获取笔记作者ID
     */
    private Long getNoteAuthorId(Long noteId) {
        try {
            Long notebookId = noteMapper.selectNotebookIdByNoteId(noteId);
            if (notebookId == null) return null;
            
            Long spaceId = notebookMapper.selectSpaceIdByNotebookId(notebookId);
            if (spaceId == null) return null;
            
            return noteSpaceMapper.selectUserIdBySpaceId(spaceId);
        } catch (Exception e) {
            log.error("获取笔记作者ID失败", e);
            return null;
        }
    }

    /**
     * 获取管理员用户ID
     */
    private Long getAdminUserId() {
        try {
            // 通过管理员邮箱查找真实的管理员用户ID
            String adminEmail = "chennzh5@mail2.sysu.edu.cn";
            UserEntity adminUser = userMapper.selectByEmail(adminEmail);
            
            if (adminUser != null && adminUser.getId() != null) {
                return adminUser.getId();
            }
            
            // 如果找不到管理员用户，尝试通过用户名查找（备用方案）
            String[] adminUsernames = {"admin", "administrator", "管理员"};
            for (String username : adminUsernames) {
                UserEntity adminByUsername = userMapper.selectByUsername(username);
                if (adminByUsername != null && adminByUsername.getId() != null && "Admin".equals(adminByUsername.getRole())) {
                    return adminByUsername.getId();
                }
            }
            
            log.error("无法找到管理员用户（邮箱: {}, 尝试的用户名: {}）", 
                adminEmail, String.join(", ", adminUsernames));
            return null;
        } catch (Exception e) {
            log.error("获取管理员用户ID失败", e);
            return null;
        }
    }
}
