package com.project.login.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequest {
    private String conversationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private LocalDateTime timestamp;
}

