package com.project.login.controller;

import com.project.login.convert.SearchConvert;
import com.project.login.model.dto.search.NoteSearchDTO;
import com.project.login.model.request.search.NoteSearchRequest;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.NoteSearchVO;
import com.project.login.model.vo.qa.QuestionVO;
import com.project.login.service.search.SearchQAService;
import com.project.login.service.search.SearchService;
import com.project.login.service.flink.userbahavior.UserSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Search", description = "Search notes and questions using Elasticsearch")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchQAService searchQAService;

    @Qualifier("searchConvert")
    private final SearchConvert convert;
    private final UserSearchService userSearchService;

    @Operation(summary = "Search notes by keyword with aggregations and score ranking")
    @PostMapping("/notes")
    public StandardResponse<List<NoteSearchVO>> searchNotes(
            @Valid @RequestBody NoteSearchRequest request
    ) {
        // 1. 转换为 DTO
        NoteSearchDTO dto = convert.toSearchDTO(request);

        // 2. 调用 Service 执行搜索
        List<NoteSearchVO> results = searchService.searchNotes(dto);

        // 3. 记录用户搜索行为
        if (request.getUserId() != null && request.getKeyword() != null) {
            userSearchService.recordSearch(request.getUserId(), request.getKeyword());
        }

        return StandardResponse.success(results);
    }

    @Operation(summary = "Search questions (QA) by keyword with score ranking")
    @GetMapping("/questions")
    public StandardResponse<List<QuestionVO>> searchQuestions(
            @RequestParam String keyword,
            @RequestParam(required = false) Long userId
    ) {
        // 1. 调用 QA 搜索 Service
        List<QuestionVO> results = searchQAService.searchQuestions(keyword);

        // 2. 记录用户搜索行为
        if (userId != null && keyword != null && !keyword.isBlank()) {
            userSearchService.recordSearch(userId, keyword);
        }

        return StandardResponse.success(results);
    }
}


