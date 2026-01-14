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

    private Long spaceId;  // 笔记空间ID

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 作者信息
    private String authorName;  // 用户名
    private String authorEmail;  // 邮箱

}
