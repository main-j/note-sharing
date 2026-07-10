package com.project.login.controller;

import com.project.login.mapper.UserFavoriteNoteMapper;
import com.project.login.mapper.UserLikeNoteMapper;
import com.project.login.model.dataobject.UserFavoriteNoteDO;
import com.project.login.model.dataobject.UserLikeNoteDO;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Note Stats", description = "High-frequency note statistics")
@RestController
@RequestMapping("/api/v1/noting/note-stats")
@RequiredArgsConstructor
public class NoteStatsController {

    private final NoteStatsService noteStatsService;
    private final UserBehaviorService userBehaviorService;
    private final UserFavoriteNoteMapper userFavoriteNoteMapper;
    private final UserLikeNoteMapper userLikeNoteMapper;
    private final NotificationService notificationService;

    @Operation(summary = "Increment/Decrement a note statistic field")
    @PostMapping("/change")
    @Transactional
    public StandardResponse<NoteStatsVO> change(
            @Valid @RequestParam Long noteId,
            @RequestParam Long userId,
            @RequestParam String field,
            @RequestParam(defaultValue = "1") long delta) {

        String fieldLower = field.toLowerCase();

        // 点赞/收藏：先校验关系是否已存在，避免重复操作导致计数漂移
        if ("likes".equals(fieldLower)) {
            boolean alreadyLiked = userLikeNoteMapper.exists(userId, noteId) > 0;
            if ((delta > 0 && alreadyLiked) || (delta < 0 && !alreadyLiked)) {
                return StandardResponse.success(enrichWithUserState(noteStatsService.getStats(noteId), userId));
            }
        } else if ("favorites".equals(fieldLower)) {
            boolean alreadyFavorited = userFavoriteNoteMapper.exists(userId, noteId) > 0;
            if ((delta > 0 && alreadyFavorited) || (delta < 0 && !alreadyFavorited)) {
                return StandardResponse.success(enrichWithUserState(noteStatsService.getStats(noteId), userId));
            }
        }

        NoteStatsVO vo = noteStatsService.changeField(noteId, field, delta);

        UserBehaviorDTO dto = new UserBehaviorDTO();
        dto.setUserId(userId);
        dto.setTargetId(noteId);

        switch (fieldLower) {
            case "views":
                dto.setBehaviorType(BehaviorType.VIEW);
                break;
            case "likes":
                dto.setBehaviorType(BehaviorType.LIKE);
                if (delta > 0) {
                    UserLikeNoteDO like = new UserLikeNoteDO();
                    like.setUserId(userId);
                    like.setNoteId(noteId);
                    userLikeNoteMapper.insert(like);
                } else {
                    userLikeNoteMapper.delete(userId, noteId);
                }
                break;
            case "favorites":
                dto.setBehaviorType(BehaviorType.FAVORITE);
                if (delta > 0) {
                    UserFavoriteNoteDO favorite = new UserFavoriteNoteDO();
                    favorite.setUserId(userId);
                    favorite.setNoteId(noteId);
                    userFavoriteNoteMapper.insert(favorite);
                } else {
                    userFavoriteNoteMapper.delete(userId, noteId);
                }
                break;
            case "comments":
                dto.setBehaviorType(BehaviorType.COMMENT);
                break;
            default:
                throw new IllegalArgumentException("Unsupported field: " + field);
        }

        userBehaviorService.recordBehavior(dto);

        if ("likes".equals(fieldLower) && delta > 0) {
            notificationService.createNoteLikeNotification(userId, noteId);
        } else if ("favorites".equals(fieldLower) && delta > 0) {
            notificationService.createNoteFavoriteNotification(userId, noteId);
        }

        return StandardResponse.success(enrichWithUserState(vo, userId));
    }

    @Operation(summary = "Get note statistics")
    @GetMapping("/{noteId}")
    public StandardResponse<NoteStatsVO> get(
            @PathVariable Long noteId,
            @RequestParam(required = false) Long userId) {
        NoteStatsVO vo = noteStatsService.getStats(noteId);
        return StandardResponse.success(enrichWithUserState(vo, userId));
    }

    private NoteStatsVO enrichWithUserState(NoteStatsVO vo, Long userId) {
        if (userId == null || vo == null) {
            return vo;
        }
        vo.setLikedOrNot(userLikeNoteMapper.exists(userId, vo.getNoteId()) > 0);
        vo.setFavoritedOrNot(userFavoriteNoteMapper.exists(userId, vo.getNoteId()) > 0);
        return vo;
    }
}
