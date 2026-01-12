package com.project.login.model.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 系统通知 Mongo 文档
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "notifications")
public class NotificationDO {

    @Id
    private String id;

    /**
     * 接收者用户 ID
     */
    @Indexed
    private Long receiverId;

    /**
     * 触发动作的用户 ID
     */
    private Long actorId;

    /**
     * 触发动作的用户名（冗余，便于前端直接展示 “xxx 做了什么”）
     */
    private String actorName;

    /**
     * 通知类型
     */
    private NotificationType type;

    /**
     * 目标类型（NOTE / QUESTION）
     */
    private String targetType;

    /**
     * 目标 ID：
     *  - NOTE: 笔记 ID（Long 转为字符串）
     *  - QUESTION: 问题 questionId（字符串）
     */
    private String targetId;

    /**
     * 问答场景下的定位信息：回答 / 评论 / 回复 ID
     */
    private Long answerId;

    private Long commentId;

    private Long replyId;

    /**
     * 已读标记
     */
    private Boolean read;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 展示文案，如 “张三 赞同了你的笔记”
     */
    private String message;
}

