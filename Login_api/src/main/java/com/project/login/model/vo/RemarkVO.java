package com.project.login.model.vo;

import lombok.*;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemarkVO {
    /** 评论自身 ID */
    private String _id;

    /** 评论所属的笔记/动态 */
    private Long noteId;

    private Long userId;

    /** 评论用户 */
    private String username;

    /** 评论用户头像 */
    private String avatarUrl;

    /** 评论内容 */
    private String content;

    /** 评论时间 */
    private String createdAt;

    /** 父评论 ID（顶级评论为 null） */
    private String parentId;

    /** 回复的目标用户名称（用于 @） */
    private String replyToUsername;

    /** 点赞数 */
    private Long likeCount;

    /** 是否是回复 **/
    private Boolean isReply;

    /** 子评论（仅一层） */
    private List<RemarkVO> replies;

    /** 当前用户是否已经点赞 **/
    private Boolean LikedOrNot;
}
