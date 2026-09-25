package com.ebrahimmorkas.chat.message;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A persisted chat message. The ObjectId is time-ordered, so (roomId, _id) gives a stable
 * order for cursor pagination without a separate sequence.
 */
@Document("messages")
@CompoundIndex(name = "room_id_desc", def = "{'roomId': 1, '_id': -1}")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    private String id;

    private String roomId;

    private String senderId;

    private String senderName;

    private String content;

    private Instant createdAt;

    public ChatMessage(String roomId, String senderId, String senderName, String content, Instant createdAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.createdAt = createdAt;
    }
}
