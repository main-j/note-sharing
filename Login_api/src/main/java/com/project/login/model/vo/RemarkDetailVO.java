package com.project.login.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemarkDetailVO {
    private String _id;

    private Long noteId;

    private String noteTitle;

    private Long userId;

    private String username;

    private String content;

    private String createdAt;

    private String parentId;

    private String replyToUsername;

    private Boolean isReply;

    private String replyToRemarkId;
}
