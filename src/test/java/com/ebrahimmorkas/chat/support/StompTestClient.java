package com.ebrahimmorkas.chat.support;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A real STOMP-over-WebSocket client for integration tests. */
public final class StompTestClient implements AutoCloseable {

    private final WebSocketStompClient client;
    private final StompSession session;
    /** STOMP ERROR frames received from the server (their "message" header). */
    public final BlockingQueue<String> errors;

    private StompTestClient(WebSocketStompClient client, StompSession session, BlockingQueue<String> errors) {
        this.client = client;
        this.session = session;
        this.errors = errors;
    }

    public static StompTestClient connect(int port, String authorizationHeader) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(JsonMapper.builder().findAndAddModules().build());
        client.setMessageConverter(converter);
        StompHeaders connectHeaders = new StompHeaders();
        if (authorizationHeader != null) {
            connectHeaders.add("Authorization", authorizationHeader);
        }
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        CompletableFuture<StompSession> rejected = new CompletableFuture<>();
        CompletableFuture<StompSession> connected = client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                                byte[] payload, Throwable exception) {
                        errors.add(String.valueOf(exception.getMessage()));
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        String message = String.valueOf(headers.getFirst("message"));
                        errors.add(message);
                        // An ERROR frame before CONNECTED means the server refused the connection
                        rejected.completeExceptionally(new IllegalStateException("Connection refused: " + message));
                    }
                });
        StompSession session = connected.applyToEither(rejected, s -> s).get(10, TimeUnit.SECONDS);
        return new StompTestClient(client, session, errors);
    }

    public <T> BlockingQueue<T> subscribe(String destination, Class<T> type) {
        BlockingQueue<T> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(type.cast(payload));
            }
        });
        return received;
    }

    public void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    public boolean isConnected() {
        return session.isConnected();
    }

    @Override
    public void close() {
        try {
            session.disconnect();
        } catch (MessageDeliveryException | IllegalStateException alreadyClosed) {
            // The server may have closed the socket already (e.g. after an ERROR frame)
        }
        client.stop();
    }
}
