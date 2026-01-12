package com.project.login.controller;

import com.project.login.mapper.UserFavoriteNoteMapper;
import com.project.login.model.dataobject.UserFavoriteNoteDO;
import com.project.login.model.dto.userbehavior.BehaviorType;
import com.project.login.model.dto.userbehavior.UserBehaviorDTO;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.NoteStatsVO;
import com.project.login.service.notestats.NoteStatsService;
import com.project.login.service.notification.NotificationService;
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
    private final UserFavoriteNoteMapper userFavoriteNoteMapper;
    private final NotificationService notificationService;

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
                // 处理收藏关系：如果 delta > 0 表示收藏，否则表示取消收藏
                if (delta > 0) {
                    // 收藏：插入或更新收藏关系
                    UserFavoriteNoteDO favorite = new UserFavoriteNoteDO();
                    favorite.setUserId(userId);
                    favorite.setNoteId(noteId);
                    userFavoriteNoteMapper.insert(favorite);
                } else {
                    // 取消收藏：删除收藏关系
                    userFavoriteNoteMapper.delete(userId, noteId);
                }
                break;
            case "comments":
                dto.setBehaviorType(BehaviorType.COMMENT);
                break;
            default:
                throw new IllegalArgumentException("Unsupported field: " + field);
        }

        // 记录用户行为
        userBehaviorService.recordBehavior(dto);

        // 创建相应通知（仅在增加时发送，避免取消点赞/收藏也发通知）
        if ("likes".equalsIgnoreCase(field) && delta > 0) {
            notificationService.createNoteLikeNotification(userId, noteId);
        } else if ("favorites".equalsIgnoreCase(field) && delta > 0) {
            notificationService.createNoteFavoriteNotification(userId, noteId);
        }

        return StandardResponse.success(vo);
    }


    @Operation(summary = "Get note statistics")
    @GetMapping("/{noteId}")
    public StandardResponse<NoteStatsVO> get(@PathVariable Long noteId) {
        NoteStatsVO vo = noteStatsService.getStats(noteId);
        return StandardResponse.success(vo);
    }
}
