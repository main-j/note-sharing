package com.project.login.model.request.moderation;

import lombok.Data;

@Data
public class ReviewNoteRequest {
    private Long moderationId;  // 审查记录ID
    private Boolean approved;   // true=通过, false=未通过
    private String adminComment; // 管理员备注
}
