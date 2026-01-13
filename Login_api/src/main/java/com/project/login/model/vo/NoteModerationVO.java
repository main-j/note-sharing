package com.project.login.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteModerationVO {
    private Long id;
    private Long noteId;
    private String noteTitle;  // 笔记标题（需要关联查询）
    private String status;      // SAFE, FLAGGED, ERROR
    private String riskLevel;   // LOW, MEDIUM, HIGH
    private Integer score;      // 0-100
    private List<String> categories;  // 违规类别列表
    private List<Object> findings;    // 具体发现项列表
    private String source;      // LLM, FAST_FILTER, DEEP_FILTER
    private LocalDateTime createdAt;
    private Boolean isHandled;
    private String adminComment;
}
