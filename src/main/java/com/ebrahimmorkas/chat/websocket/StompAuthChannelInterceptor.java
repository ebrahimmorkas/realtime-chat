package com.ebrahimmorkas.chat.websocket;

import com.ebrahimmorkas.chat.room.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates and authorizes STOMP frames.
 *
 * <p>Browsers can't set headers on the WebSocket upgrade request, so the JWT travels in the
 * STOMP CONNECT frame instead. It's validated with the same decoder as the REST API, and the
 * resulting principal is bound to the session. SUBSCRIBE to a room topic is allowed only for
 * room members, so nobody can eavesdrop by guessing a room id.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms/([^/]+)$");

    private final JwtDecoder jwtDecoder;
    private final RoomService roomService;
    private final JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> requireAuthenticated(accessor);
            default -> {
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token");
        }
        try {
            accessor.setUser(authenticationConverter.convert(jwtDecoder.decode(header.substring(7))));
        } catch (JwtException e) {
            throw new MessagingException("Invalid token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        requireAuthenticated(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Missing destination");
        }
        Matcher room = ROOM_TOPIC.matcher(destination);
        if (room.matches()) {
            if (!roomService.isMember(accessor.getUser().getName(), room.group(1))) {
                throw new MessagingException("Not a member of this room");
            }
        } else if (!destination.startsWith("/user/")) {
            throw new MessagingException("Unknown destination " + destination);
        }
    }

    private static void requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("Not authenticated");
        }
    }
}
