package com.project.login.model.dataobject;

/**
 * 系统通知类型
 */
public enum NotificationType {

    // 笔记相关
    LIKE_NOTE,                 // 喜欢/赞同我的笔记
    FAVORITE_NOTE,             // 收藏我的笔记
    NOTE_COMMENT,              // 评论我的笔记（一级评论）
    NOTE_COMMENT_REPLY,        // 评论我的评论（笔记下的回复）

    // 问答相关
    ANSWER_QUESTION,           // 回答了我的问题
    LIKE_QUESTION,             // 喜欢/赞同我的问题
    LIKE_ANSWER,               // 喜欢/赞同我的回答
    LIKE_QA_COMMENT,           // 喜欢/赞同我在问答下的评论
    LIKE_QA_REPLY,             // 喜欢/赞同我在问答下的回复
    FAVORITE_QUESTION,         // 收藏我的问题
    QA_COMMENT,                // 评论我的回答
    QA_REPLY,                  // 回复我的评论/回复

    // 动态相关
    FOLLOWEE_PUBLISH_NOTE,     // 我关注的人发布了笔记
    FOLLOWEE_PUBLISH_QUESTION, // 我关注的人发布了问题
    
    // 审查相关
    NOTE_MODERATION_APPROVED,   // 笔记审查通过
    NOTE_MODERATION_REJECTED    // 笔记审查未通过
}

