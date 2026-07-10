package com.project.login.controller;

import com.project.login.model.dto.qa.*; // 引入 DTOs
import com.project.login.model.request.qa.*;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.qa.*; // 引入 VO
import com.project.login.service.qa.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "QA", description = "Manage questions and answers")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/qa")
public class QAController {

    private final QuestionService qaService;

    @GetMapping("/questions")
    @Operation(summary = "List all questions")
    public StandardResponse<List<QuestionVO>> listQuestions() {
        return StandardResponse.success(qaService.getAllQuestions());
    }

    // Question
    @PostMapping("/question")
    @Operation(summary = "Create a new question")
    public StandardResponse<QuestionVO> createQuestion(
            @Parameter(description = "Question creation data", required = true)
            @RequestBody QuestionCreateDTO dto
    ) {
        QuestionVO vo = qaService.createQuestion(dto);
        return StandardResponse.success(vo);
    }

    @DeleteMapping("/question/{id}")
    @Operation(summary = "Delete a question")
    public StandardResponse<Void> deleteQuestion(@PathVariable String id) {
        qaService.deleteQuestion(id);
        return StandardResponse.success("删除成功",null);
    }

    @PostMapping("/question/like")
    @Operation(summary = "Like a question")
    public StandardResponse<Void> likeQuestion(
            @Parameter(description = "Like data", required = true)
            @RequestBody LikeRequest rq
    ) {
        qaService.likeQuestion(rq.getUserId(), rq.getQuestionId());
        return StandardResponse.success("点赞成功",null);
    }

    @PostMapping("/question/favorite")
    @Operation(summary = "Favorite a question")
    public StandardResponse<Void> favoriteQuestion(
            @Parameter(description = "Favorite data", required = true)
            @RequestBody FavoriteRequest rq
    ) {
        qaService.favoriteQuestion(rq.getUserId(), rq.getQuestionId());
        return StandardResponse.success(null);
    }

    @GetMapping("/question/{id}")
    @Operation(summary = "Get question details")
    public StandardResponse<QuestionVO> getQuestionDetail(@PathVariable String id) {
        QuestionVO vo = qaService.getQuestion(id);
        return StandardResponse.success(vo);
    }

    // Answer
    @PostMapping("/answer")
    @Operation(summary = "Create a new answer")
    public StandardResponse<AnswerVO> createAnswer(
            @Parameter(description = "Answer creation data", required = true)
            @RequestBody AnswerCreateDTO dto
    ) {
        AnswerVO vo = qaService.createAnswer(dto);
        return StandardResponse.success(vo);
    }

    @DeleteMapping("/answer")
    @Operation(summary = "Delete an answer")
    public StandardResponse<Void> deleteAnswer(
            @Parameter(description = "Answer deletion data", required = true)
            @RequestBody DeleteAnswerRequest rq
    ) {
        qaService.deleteAnswer(rq.getQuestionId(), rq.getAnswerId());
        return StandardResponse.success(null);
    }

    @PostMapping("/answer/like")
    @Operation(summary = "Like an answer")
    public StandardResponse<Void> likeAnswer(
            @Parameter(description = "Like data", required = true)
            @RequestBody LikeRequest rq
    ) {
        qaService.likeAnswer(rq.getUserId(), rq.getQuestionId(), rq.getAnswerId());
        return StandardResponse.success(null);
    }

    // Comment
    @PostMapping("/comment")
    @Operation(summary = "Create a new comment")
    public StandardResponse<CommentVO> createComment(
            @Parameter(description = "Comment creation data", required = true)
            @RequestBody CommentCreateDTO dto
    ) {
        CommentVO vo = qaService.createComment(dto);
        return StandardResponse.success(vo);
    }

    @DeleteMapping("/comment")
    @Operation(summary = "Delete a comment")
    public StandardResponse<Void> deleteComment(
            @Parameter(description = "Comment deletion data", required = true)
            @RequestBody DeleteCommentRequest rq
    ) {
        qaService.deleteComment(rq.getQuestionId(), rq.getAnswerId(), rq.getCommentId());
        return StandardResponse.success(null);
    }

    @PostMapping("/comment/like")
    @Operation(summary = "Like a comment")
    public StandardResponse<Void> likeComment(
            @Parameter(description = "Like data", required = true)
            @RequestBody LikeRequest rq
    ) {
        qaService.likeComment(rq.getUserId(), rq.getQuestionId(), rq.getAnswerId(), rq.getCommentId());
        return StandardResponse.success(null);
    }

    // Reply
    @PostMapping("/reply")
    @Operation(summary = "Create a new reply")
    public StandardResponse<ReplyVO> createReply(
            @Parameter(description = "Reply creation data", required = true)
            @RequestBody ReplyCreateDTO dto
    ) {
        ReplyVO vo = qaService.createReply(dto);
        return StandardResponse.success(vo);
    }

    @DeleteMapping("/reply")
    @Operation(summary = "Delete a reply")
    public StandardResponse<Void> deleteReply(
            @Parameter(description = "Reply deletion data", required = true)
            @RequestBody DeleteReplyRequest rq
    ) {
        qaService.deleteReply(rq.getQuestionId(), rq.getAnswerId(), rq.getCommentId(), rq.getReplyId());
        return StandardResponse.success(null);
    }

    @PostMapping("/reply/like")
    @Operation(summary = "Like a reply")
    public StandardResponse<Void> likeReply(
            @Parameter(description = "Like data", required = true)
            @RequestBody LikeRequest rq
    ) {
        qaService.likeReply(
                rq.getUserId(),
                rq.getQuestionId(),
                rq.getAnswerId(),
                rq.getCommentId(),
                rq.getReplyId()
        );
        return StandardResponse.success(null);
    }
}
