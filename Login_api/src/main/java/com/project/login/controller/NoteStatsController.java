package com.project.login.controller;

import com.project.login.model.dto.userbehavior.BehaviorType;
import com.project.login.model.dto.userbehavior.UserBehaviorDTO;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.NoteStatsVO;
import com.project.login.service.notestats.NoteStatsService;
import com.project.login.service.flink.userbahavior.UserBehaviorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Note Stats", description = "High-frequency note statistics")
@RestController
@RequestMapping("/api/v1/noting/note-stats")
@RequiredArgsConstructor
public class NoteStatsController {

    private final NoteStatsService noteStatsService;
    private final UserBehaviorService userBehaviorService;

    @Operation(summary = "Increment/Decrement a note statistic field")
    @PostMapping("/change")
    public StandardResponse<NoteStatsVO> change(
            @Valid @RequestParam Long noteId,
            @RequestParam Long userId,
            @RequestParam String field,
            @RequestParam(defaultValue = "1") long delta) {

        // 更新笔记统计
        NoteStatsVO vo = noteStatsService.changeField(noteId, field, delta);

        // 构建用户行为记录
        UserBehaviorDTO dto = new UserBehaviorDTO();
        dto.setUserId(userId);
        dto.setTargetId(noteId);

        // 根据字段设置行为类型
        switch (field.toLowerCase()) {
            case "views":
                dto.setBehaviorType(BehaviorType.VIEW);
                break;
            case "likes":
                dto.setBehaviorType(BehaviorType.LIKE);
                break;
            case "favorites":
                dto.setBehaviorType(BehaviorType.FAVORITE);
                break;
            case "comments":
                dto.setBehaviorType(BehaviorType.COMMENT);
                break;
            default:
                throw new IllegalArgumentException("Unsupported field: " + field);
        }

        // 记录用户行为
        userBehaviorService.recordBehavior(dto);

        return StandardResponse.success(vo);
    }


    @Operation(summary = "Get note statistics")
    @GetMapping("/{noteId}")
    public StandardResponse<NoteStatsVO> get(@PathVariable Long noteId) {
        NoteStatsVO vo = noteStatsService.getStats(noteId);
        return StandardResponse.success(vo);
    }
}
