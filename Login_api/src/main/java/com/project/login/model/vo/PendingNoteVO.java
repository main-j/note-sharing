package com.project.login.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待审核笔记VO，包含笔记信息和审查信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingNoteVO {
    // 笔记信息
    private Long noteId;
    private String noteTitle;
    private String fileType;
    private String filename;
    private String fileUrl;
    private Long notebookId;
    private Long authorId;
    private String authorName;
    private LocalDateTime noteCreatedAt;
    private LocalDateTime noteUpdatedAt;
    
    // 审查信息
    private Long moderationId;
    private String status;           // SAFE, FLAGGED
    private String riskLevel;        // LOW, MEDIUM, HIGH
    private Integer score;           // 风险评分 0-100
    private List<String> categories; // 违规类别列表
    private List<Object> findings;   // 具体发现项列表
    private String source;           // LLM, FAST_FILTER, DEEP_FILTER
    private LocalDateTime moderationCreatedAt;
    private Boolean isHandled;
    private String adminComment;
}
