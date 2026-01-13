package com.project.login.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 关注用户信息VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUserVO {
    private Long userId;           // 被关注用户ID
    private String username;       // 被关注用户名
    private String email;          // 被关注用户邮箱
    private String avatarUrl;      // 被关注用户头像URL
    private LocalDateTime followTime;  // 关注时间
}
