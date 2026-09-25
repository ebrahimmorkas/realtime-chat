package com.ebrahimmorkas.chat.websocket;

import com.ebrahimmorkas.chat.message.ChatMessageRepository;
import com.ebrahimmorkas.chat.message.MessageResponse;
import com.ebrahimmorkas.chat.message.SendMessageRequest;
import com.ebrahimmorkas.chat.support.ChatTestClient;
import com.ebrahimmorkas.chat.support.IntegrationTest;
import com.ebrahimmorkas.chat.support.StompTestClient;
import com.ebrahimmorkas.chat.support.TestcontainersConfiguration;
import com.ebrahimmorkas.chat.websocket.ChatMessageController.ChatError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChatWebSocketIntegrationTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    private String alice;
    private String bob;
    private String mallory;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        String aliceEmail = uniqueEmail();
        String bobEmail = uniqueEmail();
        String malloryEmail = uniqueEmail();
        register(aliceEmail);
        register(bobEmail);
        register(malloryEmail);
        alice = "Bearer " + login(aliceEmail);
        bob = "Bearer " + login(bobEmail);
        mallory = "Bearer " + login(malloryEmail);
        roomId = new ChatTestClient(mockMvc, objectMapper).createRoom(alice, "Team", List.of(bobEmail));
    }

    @Test
    void messageIsPersistedAndDeliveredToEveryRoomSubscriber() throws Exception {
        try (StompTestClient aliceWs = StompTestClient.connect(port, alice);
             StompTestClient bobWs = StompTestClient.connect(port, bob)) {
            BlockingQueue<MessageResponse> bobInbox = bobWs.subscribe("/topic/rooms/" + roomId, MessageResponse.class);
            BlockingQueue<MessageResponse> aliceInbox = aliceWs.subscribe("/topic/rooms/" + roomId, MessageResponse.class);
            awaitSubscribed(bob, "/topic/rooms/" + roomId);
            awaitSubscribed(alice, "/topic/rooms/" + roomId);

            aliceWs.send("/app/rooms/" + roomId + "/send", new SendMessageRequest("  Hello team!  "));

            MessageResponse received = bobInbox.poll(10, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.content()).isEqualTo("Hello team!");
            assertThat(received.senderName()).isEqualTo("Test User");
            assertThat(aliceInbox.poll(10, TimeUnit.SECONDS)).isNotNull();
            assertThat(messageRepository.findById(received.id())).isPresent();
        }
    }

    @Test
    void nonMembersCannotSubscribeToARoom() throws Exception {
        try (StompTestClient malloryWs = StompTestClient.connect(port, mallory)) {
            malloryWs.subscribe("/topic/rooms/" + roomId, MessageResponse.class);

            assertThat(malloryWs.errors.poll(10, TimeUnit.SECONDS)).contains("Not a member");
        }
    }

    @Test
    void nonMembersCannotPostIntoARoom() throws Exception {
        try (StompTestClient malloryWs = StompTestClient.connect(port, mallory)) {
            BlockingQueue<ChatError> errors = malloryWs.subscribe("/user/queue/errors", ChatError.class);
            awaitSubscribed(mallory, "/queue/errors");

            malloryWs.send("/app/rooms/" + roomId + "/send", new SendMessageRequest("let me in"));

            ChatError error = errors.poll(10, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.code()).isEqualTo("REJECTED");
        }
        assertThat(messageRepository.findAll()).noneMatch(m -> m.getContent().equals("let me in"));
    }

    @Test
    void blankMessagesAreRejectedPrivately() throws Exception {
        try (StompTestClient aliceWs = StompTestClient.connect(port, alice)) {
            BlockingQueue<ChatError> errors = aliceWs.subscribe("/user/queue/errors", ChatError.class);
            awaitSubscribed(alice, "/queue/errors");

            aliceWs.send("/app/rooms/" + roomId + "/send", new SendMessageRequest("   "));

            ChatError error = errors.poll(10, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.code()).isEqualTo("INVALID_MESSAGE");
        }
    }

    @Test
    void connectionWithoutValidTokenIsRefused() {
        assertThatThrownBy(() -> StompTestClient.connect(port, null))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Missing bearer token");
        assertThatThrownBy(() -> StompTestClient.connect(port, "Bearer not-a-jwt"))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Invalid token");
    }

    private void awaitSubscribed(String token, String destinationSuffix) {
        String userId = jwtDecoder.decode(token.substring("Bearer ".length())).getSubject();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                userRegistry.findSubscriptions(s -> s.getSession().getUser().getName().equals(userId)
                        && s.getDestination().endsWith(destinationSuffix)).size() == 1);
    }
}
