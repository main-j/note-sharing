package com.project.login.model.request.moderation;

import lombok.Data;
import java.util.List;

@Data
public class SubmitModerationRequest {
    private Long noteId;              // 笔记ID
    private String status;            // SAFE, FLAGGED
    private String riskLevel;         // LOW, MEDIUM, HIGH
    private Double score;             // 风险评分 0-100
    private List<String> categories;  // 类别列表
    private List<Finding> findings;  // 发现项列表

    @Data
    public static class Finding {
        private String term;          // 敏感词
        private String category;      // 类别
        private Double confidence;    // 置信度
        private String snippet;       // 上下文片段
        private Integer startOffset; // 起始位置
        private Integer endOffset;    // 结束位置
    }
}
