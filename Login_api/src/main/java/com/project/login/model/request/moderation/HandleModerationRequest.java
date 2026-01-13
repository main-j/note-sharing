package com.project.login.model.request.moderation;

import lombok.Data;

@Data
public class HandleModerationRequest {
    private Boolean isHandled;      // 是否已处理
    private String adminComment;    // 管理员备注
}
