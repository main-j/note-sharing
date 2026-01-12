package com.project.login.controller;

import com.project.login.service.conversationService.ChatUnreadService;
import com.project.login.service.conversationService.ConversationService;
import com.project.login.service.userFollow.UserFollowService;
import com.project.login.model.request.ChatMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final UserFollowService followService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatUnreadService chatUnreadService;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessageRequest req, Principal principal) {

        Long senderId = Long.valueOf(principal.getName());
        Long receiverId = req.getReceiverId();

        // 参数校验
        if (receiverId == null || req.getContent() == null || req.getContent().trim().isEmpty()) {
            return;
        }

        // 必须互关
        if (!followService.isMutualFollow(senderId, receiverId)) {
            return;
        }

        // 持久化消息
        String conversationId =
                conversationService.sendMessage(senderId, receiverId, req.getContent());

        // 如果conversationId为null，说明发送失败（用户不存在等情况），直接返回
        if (conversationId == null) {
            return;
        }

        // 构造推送消息
        ChatMessageRequest push = ChatMessageRequest.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .receiverId(receiverId)
                .content(req.getContent())
                .timestamp(LocalDateTime.now())
                .build();

        // 接收方未读 +1 或推送
        if (!chatUnreadService.isUserOnline(receiverId,conversationId)) {
            // 离线 → 增加未读
            chatUnreadService.incr(receiverId, conversationId);
        } else {
            // 在线 → 推送到该会话队列
            messagingTemplate.convertAndSendToUser(
                    receiverId.toString(),
                    "/queue/messages." + conversationId, // 多队列模式
                    push
            );
        }

        // 发送者本地显示 → 推送到自己当前会话队列
        messagingTemplate.convertAndSendToUser(
                senderId.toString(),
                "/queue/messages." + conversationId, // 多队列模式
                push
        );
    }
}
