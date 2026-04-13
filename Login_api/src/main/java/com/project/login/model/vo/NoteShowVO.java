package com.project.login.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoteShowVO {

    private Long id;
    private String title;
    private String url;
    private String fileType;

    private Long notebookId;

    private Long spaceId;

    private Boolean fileExists;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String authorName;
    private String authorEmail;
}
