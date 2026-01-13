package com.project.login.controller;

import com.project.login.model.dto.remark.RemarkDeleteDTO;
import com.project.login.model.dto.remark.RemarkInsertDTO;
import com.project.login.model.dto.remark.RemarkSelectByNoteDTO;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.RemarkVO;
import com.project.login.service.remark.RemarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Remark", description = "Comment and like operations")
@RestController
@RequestMapping("/api/v1/remark")
@RequiredArgsConstructor
public class RemarkController {

    private final RemarkService remarkService;

    @Operation(summary = "Get comments by note ID")
    @GetMapping("/note/list")
    public StandardResponse<List<RemarkVO>> getRemarksByNote(
            @Valid RemarkSelectByNoteDTO dto,
            @RequestParam Long loginUserId
    ) {
        List<RemarkVO> voList = remarkService.SelectRemark(dto, loginUserId);
        return StandardResponse.success(voList);
    }

    @Operation(summary = "Insert a new comment")
    @PostMapping("/insert")
    public StandardResponse<Boolean> insertRemark(@Valid @RequestBody RemarkInsertDTO dto) {
        Boolean result = remarkService.insertRemark(dto);
        return StandardResponse.success(result);
    }

    @Operation(summary = "Delete a comment")
    @PostMapping("/delete")
    public StandardResponse<Boolean> deleteRemark(@Valid @RequestBody RemarkDeleteDTO dto) {
        Boolean result = remarkService.deleteRemark(dto);
        return StandardResponse.success(result);
    }

    @Operation(summary = "Like a comment")
    @PostMapping("/like")
    public StandardResponse<Boolean> likeRemark(
            @RequestParam String remarkId,
            @RequestParam Long loginUserId
    ) {
        Boolean result = remarkService.likeRemark(remarkId, loginUserId);
        return StandardResponse.success(result);
    }

    @Operation(summary = "Cancel like on a comment")
    @PostMapping("/cancelLike")
    public StandardResponse<Boolean> cancelLikeRemark(
            @RequestParam String remarkId,
            @RequestParam Long loginUserId
    ) {
        Boolean result = remarkService.cancelLikeRemark(remarkId, loginUserId);
        return StandardResponse.success(result);
    }

    @Operation(summary = "Get comment tree by remark ID")
    @GetMapping("/tree")
    public StandardResponse<RemarkVO> getRemarkTree(
            @RequestParam String remarkId,
            @RequestParam Long loginUserId
    ) {
        RemarkVO remarkTree = remarkService.getRemarkTreeByRemarkId(remarkId, loginUserId);
        return StandardResponse.success(remarkTree);
    }

}
