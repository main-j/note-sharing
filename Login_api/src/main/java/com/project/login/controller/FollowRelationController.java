package com.project.login.controller;

import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.followRelation.GetFollowersVO;
import com.project.login.model.vo.followRelation.GetFollowingsVO;
import com.project.login.service.followRelation.FollowRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FollowRelation", description = "Follow and follower operations")
@RestController
@RequestMapping("/api/v1/followRelation")
@RequiredArgsConstructor
public class FollowRelationController {

    private final FollowRelationService followService;

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
        int code=followService.follow(userId, targetUserId);
        if(code==-1){
            return StandardResponse.success("Cannot follow yourself",false);
        }
        else if(code==-2){
            return StandardResponse.success("Already followed",false);
        }
        else if(code==-3){
            return StandardResponse.success("user not exist",false);
        }
        else{
            return StandardResponse.success(true);
        }
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

}
