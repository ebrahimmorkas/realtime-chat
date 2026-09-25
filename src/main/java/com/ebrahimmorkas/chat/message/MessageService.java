package com.ebrahimmorkas.chat.message;

import com.ebrahimmorkas.chat.room.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageRepository messageRepository;
    private final RoomService roomService;
    private final MessageBroadcaster broadcaster;
    private final Clock clock;

    /** Persists first, then broadcasts, so every message a client sees is also in history. */
    public MessageResponse send(String roomId, String senderId, String senderName, String content) {
        roomService.requireMembership(senderId, roomId);
        ChatMessage saved = messageRepository.save(
                new ChatMessage(roomId, senderId, senderName, content.strip(), clock.instant()));
        MessageResponse response = MessageResponse.from(saved);
        broadcaster.broadcast(response);
        return response;
    }
}
