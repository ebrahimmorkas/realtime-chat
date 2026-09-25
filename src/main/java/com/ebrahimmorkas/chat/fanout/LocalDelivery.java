package com.ebrahimmorkas.chat.fanout;

import com.ebrahimmorkas.chat.message.MessageBroadcaster;
import com.ebrahimmorkas.chat.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes a message to the WebSocket clients connected to <em>this</em> instance. */
@Component
@RequiredArgsConstructor
public class LocalDelivery {

    private final SimpMessagingTemplate messagingTemplate;

    public void deliver(MessageResponse message) {
        messagingTemplate.convertAndSend(MessageBroadcaster.roomTopic(message.roomId()), message);
    }
}
