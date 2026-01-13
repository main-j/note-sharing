package com.project.login.model.vo.qa;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnswerVO {
    private Long answerId;
    private Long authorId;
    private String authorName;  // 回答者用户名
    private String content;
    private LocalDateTime createdAt;

    private Integer likeCount;

    private List<CommentVO> comments;
}



