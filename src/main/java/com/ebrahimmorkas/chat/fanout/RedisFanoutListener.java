package com.ebrahimmorkas.chat.fanout;

import com.ebrahimmorkas.chat.message.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Receives every message published by any instance and delivers it to local WebSocket clients. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFanoutListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final LocalDelivery localDelivery;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            localDelivery.deliver(objectMapper.readValue(message.getBody(), MessageResponse.class));
        } catch (IOException e) {
            log.error("Dropping malformed fan-out payload", e);
        }
    }
}
