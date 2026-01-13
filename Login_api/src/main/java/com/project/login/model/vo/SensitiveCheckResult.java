package com.project.login.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class SensitiveCheckResult {
    private String status;              // SAFE, FLAGGED, ERROR
    private String riskLevel;           // LOW, MEDIUM, HIGH
    private Double score;               // 0-100
    private List<String> categories;    // profanity, violence, hate, sexual, politics, others
    private List<Finding> findings;    // 具体发现项
    private NoteMeta noteMeta;          // 笔记元信息
    private String model;               // 使用的模型
    private String checkedAt;           // 检查时间
    private Long durationMs;             // 耗时（毫秒）
    private TokenUsage tokenUsage;       // Token使用情况
    private String message;              // 错误消息

    @Data
    public static class Finding {
        private String term;             // 敏感词
        private String category;        // 类别
        private Double confidence;       // 置信度 0-1
        private String snippet;          // 上下文片段
        private Integer startOffset;    // 起始位置
        private Integer endOffset;      // 结束位置
    }

    @Data
    public static class NoteMeta {
        private Long noteId;
        private String title;
    }

    @Data
    public static class TokenUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
