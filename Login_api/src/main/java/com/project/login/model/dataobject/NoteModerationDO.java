package com.project.login.model.dataobject;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoteModerationDO {
    private Long id;
    private Long noteId;
    private String status;
    private String riskLevel;
    private Integer score;
    private String categoriesJson;
    private String findingsJson;
    private String source;
    private LocalDateTime createdAt;
    private Boolean isHandled;
    private String adminComment;
}
