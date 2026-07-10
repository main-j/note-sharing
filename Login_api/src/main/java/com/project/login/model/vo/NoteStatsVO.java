package com.project.login.model.vo;

import lombok.Data;

@Data
public class NoteStatsVO {
    private Long noteId;
    private String authorName;
    private Long views;
    private Long likes;
    private Long favorites;
    private Long comments;
    /** 当前用户是否已点赞（仅当请求携带 userId 时有值） */
    private Boolean likedOrNot;
    /** 当前用户是否已收藏（仅当请求携带 userId 时有值） */
    private Boolean favoritedOrNot;
}
