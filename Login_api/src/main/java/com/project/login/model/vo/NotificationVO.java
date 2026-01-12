package com.project.login.model.vo;

import com.project.login.model.dataobject.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 前端展示用通知 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationVO {

    private String id;

    private Long receiverId;

    private Long actorId;

    private String actorName;

    private NotificationType type;

    private String targetType;

    private String targetId;

    // 问答场景：用于前端自动滚动定位
    private Long answerId;

    private Long commentId;

    private Long replyId;

    private Boolean read;

    private LocalDateTime createdAt;

    private String message;
}

