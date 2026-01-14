package com.project.login.controller;

import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.userFollow.GetFollowersVO;
import com.project.login.model.vo.userFollow.GetFollowingsVO;
import com.project.login.service.conversationService.ConversationService;
import com.project.login.service.userFollow.UserFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FollowRelation", description = "Follow and follower operations")
@RestController
@RequestMapping("/api/v1/followRelation")
@RequiredArgsConstructor
@Slf4j
public class FollowRelationController {

    private final UserFollowService followService;
    private final ConversationService conversationService;

    @Operation(summary = "Get the list of users this user is following")
    @GetMapping("/followings")
    public StandardResponse<GetFollowingsVO> getFollowings(
            @RequestParam Long userId
    ) {
        GetFollowingsVO vo = followService.getFollowings(userId);
        return StandardResponse.success(vo);
    }

    @Operation(summary = "Get the list of followers for this user")
    @GetMapping("/followers")
    public StandardResponse<GetFollowersVO> getFollowers(
            @RequestParam Long userId
    ) {
        GetFollowersVO vo = followService.getFollowers(userId);
        return StandardResponse.success(vo);
    }

    @Operation(summary = "Follow a user")
    @PostMapping("/follow")
    public StandardResponse<Boolean> follow(
            @RequestParam Long userId,
            @RequestParam Long targetUserId
    ) {
        // 关注之前，先判断对方是否已经关注了当前用户
        // 如果对方已关注，而当前调用 follow 成功，则本次操作会把双方关系变为互相关注
        boolean targetAlreadyFollows = followService.isFollowing(targetUserId, userId);

        int code = followService.follow(userId, targetUserId);
        if (code == -1) {
            return StandardResponse.success("Cannot follow yourself", false);
        } else if (code == -2) {
            return StandardResponse.success("Already followed", false);
        }

        // code == 0 表示本次关注成功
        // 如果对方在本次操作前就已经关注了当前用户，则现在双方变为互相关注
        // 此时由“后关注者”自动发送一条欢迎私信，用于创建会话
        if (targetAlreadyFollows) {
            try {
                String content = "我已回应你的关注，我们现在可以开始会话啦！";
                conversationService.sendMessage(userId, targetUserId, content);
            } catch (Exception e) {
                // 自动欢迎消息失败不影响关注主流程，只记录日志
                log.warn("Failed to send auto welcome message after mutual follow. followerId={}, followeeId={}",
                        userId, targetUserId, e);
            }
        }

        return StandardResponse.success(true);
    }

    @Operation(summary = "Unfollow a user")
    @PostMapping("/unfollow")
    public StandardResponse<Boolean> unfollow(
            @RequestParam Long userId,
            @RequestParam Long targetUserId
    ) {
        int code = followService.unfollow(userId, targetUserId);
        if(code!=0){
            return StandardResponse.success("Never followed",false);
        }
        return StandardResponse.success(true);
    }

    @Operation(summary = "Check if user is following another user")
    @GetMapping("/isFollowing")
    public StandardResponse<Boolean> isFollowing(
            @RequestParam Long userId,
            @RequestParam Long targetUserId
    ) {
        boolean result = followService.isFollowing(userId, targetUserId);
        return StandardResponse.success(result);
    }

    @Operation(summary = "Check if two users follow each other (mutual follow)")
    @GetMapping("/isMutualFollow")
    public StandardResponse<Boolean> isMutualFollow(
            @RequestParam Long userId,
            @RequestParam Long targetUserId
    ) {
        boolean result = followService.isMutualFollow(userId, targetUserId);
        return StandardResponse.success(result);
    }


}
