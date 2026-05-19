package com.project.login.controller;

import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.NoteSearchVO;
import com.project.login.model.vo.qa.QuestionVO;
import com.project.login.service.recommend.UserProfileQueryService;
import com.project.login.service.recommend.core.HomeFeedService;
import com.project.login.service.recommend.model.HomeFeedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final UserProfileQueryService userProfileQueryService;
    private final HomeFeedService homeFeedService;

    @GetMapping("/feed")
    public StandardResponse<HomeFeedResult> recommendFeed(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        HomeFeedResult feedResult = homeFeedService.recommend(userId, pageSize);
        return StandardResponse.success(feedResult);
    }

    @GetMapping("/notes")
    public StandardResponse<List<NoteSearchVO>> recommendNotes(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "topN", defaultValue = "10") int topN) throws Exception {
        List<NoteSearchVO> recommendedNotes = userProfileQueryService.recommendNotesByKeywords(userId, topN);
        return StandardResponse.success(recommendedNotes);
    }

    @GetMapping("/QAs")
    public StandardResponse<List<QuestionVO>> recommendQAs(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "topN", defaultValue = "10") int topN) throws Exception {
        List<QuestionVO> recommendedQAs = userProfileQueryService.recommendQuestionsByKeywords(userId, topN);
        return StandardResponse.success(recommendedQAs);
    }
}
