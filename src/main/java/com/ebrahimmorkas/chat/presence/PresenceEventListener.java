package com.ebrahimmorkas.chat.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/** Feeds WebSocket session lifecycle events into the presence tracker. */
@Component
@RequiredArgsConstructor
class PresenceEventListener {

    private final PresenceTracker presenceTracker;

    @EventListener
    void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            presenceTracker.connected(user.getName());
        }
    }

    @EventListener
    void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            presenceTracker.disconnected(user.getName());
        }
    }
}
