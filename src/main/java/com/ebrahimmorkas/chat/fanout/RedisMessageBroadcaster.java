package com.ebrahimmorkas.chat.fanout;

import com.ebrahimmorkas.chat.message.MessageBroadcaster;
import com.ebrahimmorkas.chat.message.MessageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Horizontal scaling: users in the same room may be connected to different instances, so every
 * message is published to a Redis channel that all instances subscribe to
 * ({@link RedisFanoutListener}), and each one delivers it to its own sockets.
 *
 * <p>If Redis is unavailable the message is still delivered to users on this instance (and it is
 * already persisted, so others see it in history when they reload).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageBroadcaster implements MessageBroadcaster {

    static final String CHANNEL = "chat.messages";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final LocalDelivery localDelivery;

    @Override
    public void broadcast(MessageResponse message) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize message " + message.id(), e);
        } catch (RuntimeException e) {
            log.warn("Redis fan-out failed for message {} ({}); delivering locally only", message.id(), e.getMessage());
            localDelivery.deliver(message);
        }
    }
}
