package com.project.login.controller;

import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.NotificationVO;
import com.project.login.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notifications", description = "系统通知中心")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "获取当前用户最近的通知列表（最多 50 条）")
    @GetMapping("/list")
    public StandardResponse<List<NotificationVO>> list(@RequestParam Long userId) {
        List<NotificationVO> list = notificationService.listLatestForUser(userId);
        return StandardResponse.success(list);
    }

    @Operation(summary = "获取当前用户未读通知数量")
    @GetMapping("/unread/total")
    public StandardResponse<Long> unreadTotal(@RequestParam Long userId) {
        long total = notificationService.getUnreadTotal(userId);
        return StandardResponse.success(total);
    }

    @Operation(summary = "将单条通知标记为已读")
    @PostMapping("/read")
    public StandardResponse<Void> markRead(@RequestParam String notificationId) {
        notificationService.markAsRead(notificationId);
        return StandardResponse.success(null);
    }

    @Operation(summary = "将当前用户所有通知标记为已读")
    @PostMapping("/read/all")
    public StandardResponse<Void> markAllRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return StandardResponse.success(null);
    }
}

