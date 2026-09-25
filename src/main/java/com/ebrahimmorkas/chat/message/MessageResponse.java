package com.ebrahimmorkas.chat.message;

import java.time.Instant;

public record MessageResponse(String id, String roomId, String senderId, String senderName, String content,
                              Instant createdAt) {

    static MessageResponse from(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getRoomId(), message.getSenderId(),
                message.getSenderName(), message.getContent(), message.getCreatedAt());
    }
}
