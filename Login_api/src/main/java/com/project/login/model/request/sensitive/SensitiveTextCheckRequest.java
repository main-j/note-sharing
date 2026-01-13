package com.project.login.model.request.sensitive;

import lombok.Data;

@Data
public class SensitiveTextCheckRequest {
    private String text;
    private String language;  // 可选
}
