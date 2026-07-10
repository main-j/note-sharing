package com.project.login.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserLikeNoteDO {
    private Long id;
    private Long userId;
    private Long noteId;
    private LocalDateTime likeTime;
}
