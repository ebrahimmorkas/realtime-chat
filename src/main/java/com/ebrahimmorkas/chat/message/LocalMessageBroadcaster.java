package com.ebrahimmorkas.chat.message;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Delivers to clients connected to this instance only. */
@Component
@RequiredArgsConstructor
public class LocalMessageBroadcaster implements MessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcast(MessageResponse message) {
        messagingTemplate.convertAndSend(MessageBroadcaster.roomTopic(message.roomId()), message);
    }
}
