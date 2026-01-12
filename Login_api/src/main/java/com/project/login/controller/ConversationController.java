package com.project.login.controller;

import com.project.login.mapper.ConversationRelationMapper;
import com.project.login.model.dataobject.ConversationRelationDO;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.ConversationListVO;
import com.project.login.model.vo.ConversationVO;
import com.project.login.service.conversationService.ChatUnreadService;
import com.project.login.service.conversationService.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "Conversation", description = "Private chat operations")
@RestController
@RequestMapping("/api/v1/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatUnreadService chatUnreadService;
    private final ConversationRelationMapper conversationRelationMapper;

    @Operation(summary = "Get the list of conversations for a user")
    @GetMapping("/list")
    public StandardResponse<ConversationListVO> getConversationsList(
            @RequestParam Long userId
    ) {
        // 查会话列表（DB）
        ConversationListVO vo =
                conversationService.getConversationsList(userId);
        return StandardResponse.success(vo);
    }

    @Operation(summary = "Get full conversation by conversationId")
    @GetMapping("/detail")
    public StandardResponse<ConversationVO> getConversation(
            @RequestParam String conversationId
    ) {
        ConversationVO vo =
                conversationService.getConversation(conversationId);

        if (vo == null) {
            return StandardResponse.success("Conversation not found", null);
        }

        return StandardResponse.success(vo);
    }

    @Operation(summary = "Get unread count for a specific conversation")
    @GetMapping("/unread")
    public StandardResponse<Integer> getUnreadCount(
            @RequestParam Long userId,
            @RequestParam String conversationId
    ) {
        int unreadCount = chatUnreadService.getUnread(userId, conversationId);
        return StandardResponse.success(unreadCount);
    }

    @Operation(summary = "Get all unread counts for a user")
    @GetMapping("/unread/all")
    public StandardResponse<Map<Object, Object>> getAllUnreadCounts(
            @RequestParam Long userId
    ) {
        Map<Object, Object> allUnread = chatUnreadService.getAllUnread(userId);
        return StandardResponse.success(allUnread);
    }

    /**
     * 标记会话为已读（HTTP接口）
     * 只有当用户主动调用此接口时，才会标记为已读
     * 如果用户只是登录但没有打开私信界面，消息将保持未读状态
     */
    @Operation(summary = "Mark conversation as read")
    @PostMapping("/read")
    public StandardResponse<String> markConversationAsRead(
            @RequestParam Long userId,
            @RequestParam String conversationId
    ) {
        // 参数校验
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("markConversationAsRead: conversationId is null or empty, userId={}", userId);
            return StandardResponse.success("会话ID不能为空", null);
        }

        // 权限验证：检查用户是否有权限访问该会话
        ConversationRelationDO relation = conversationRelationMapper.selectByConversationId(conversationId);
        if (relation == null) {
            log.warn("markConversationAsRead: conversation not found, conversationId={}, userId={}", conversationId, userId);
            return StandardResponse.success("会话不存在", null);
        }
        
        if (!relation.getUser1Id().equals(userId) && !relation.getUser2Id().equals(userId)) {
            log.warn("markConversationAsRead: user has no permission, conversationId={}, userId={}", conversationId, userId);
            return StandardResponse.success("无权访问该会话", null);
        }

        // 清空未读
        chatUnreadService.clear(userId, conversationId);
        
        log.info("markConversationAsRead: success, conversationId={}, userId={}", conversationId, userId);
        return StandardResponse.success("标记已读成功");
    }
    @Operation(summary = "Get total unread count for a user")
    @GetMapping("/unread/total")
    public StandardResponse<Integer> getUnreadTotal(
            @RequestParam Long userId
    ) {
        int totalUnread = chatUnreadService.getUnreadTotal(userId);
        return StandardResponse.success(totalUnread);
    }

    /*
    @Operation(summary = "Send a message to another user")
    @PostMapping("/send")
    public StandardResponse<String> sendMessage(
            @RequestParam Long senderId,
            @RequestParam Long receiverId,
            @RequestParam String content
    ) {
        String conversationId =
                conversationService.sendMessage(senderId, receiverId, content);

        if (conversationId == null) {
            return StandardResponse.success("failed, please mutualFollow first", null);
        }
        return StandardResponse.success(conversationId);
    }
    */
}
