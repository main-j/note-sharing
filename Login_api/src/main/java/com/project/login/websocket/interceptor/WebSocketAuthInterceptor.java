package com.project.login.websocket.interceptor;

import com.project.login.service.login.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new RuntimeException("Missing Authorization token");
            }

            String token = auth.substring(7);
            Long userId = jwtService.extractUserId(token);

            if (userId == null || jwtService.isTokenExpired(token)) {
                throw new RuntimeException("Invalid or expired token");
            }

            // 绑定 userId 到 WebSocket Principal
            accessor.setUser(() -> userId.toString());
            System.out.println(userId);
        }

        return message;
    }
}
