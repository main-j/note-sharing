package com.project.login.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户信息（包含发布文章和关注列表）响应VO
 * 用于管理员根据邮箱查询用户信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoWithNotesAndFollowsVO {
    private Long userId;                    // 用户ID
    private String username;                // 用户名
    private String email;                   // 邮箱
    private String studentNumber;           // 学号
    private String avatarUrl;               // 头像URL
    
    private List<NoteShowVO> publishedNotes;  // 所有发布的文章列表
    private List<FollowUserVO> followings;     // 关注用户列表
}
