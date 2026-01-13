package com.project.login.controller;

import com.project.login.model.vo.RemarkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * 评论相关的 WebSocket 控制器
 * 用于实时推送新评论给所有查看该笔记的用户
 */
@Controller
@RequiredArgsConstructor
public class RemarkWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 推送新评论到所有订阅该笔记的用户
     * @param noteId 笔记ID
     * @param remarkVO 新评论数据
     */
    public void broadcastNewRemark(Long noteId, RemarkVO remarkVO) {
        // 使用 topic 模式，所有订阅该笔记的用户都能收到
        String destination = "/topic/note." + noteId + ".comments";
        messagingTemplate.convertAndSend(destination, remarkVO);
    }
}
