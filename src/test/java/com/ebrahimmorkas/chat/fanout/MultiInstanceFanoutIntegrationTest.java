package com.ebrahimmorkas.chat.fanout;

import com.ebrahimmorkas.chat.RealtimeChatApplication;
import com.ebrahimmorkas.chat.message.MessageResponse;
import com.ebrahimmorkas.chat.message.SendMessageRequest;
import com.ebrahimmorkas.chat.support.ChatTestClient;
import com.ebrahimmorkas.chat.support.IntegrationTest;
import com.ebrahimmorkas.chat.support.StompTestClient;
import com.ebrahimmorkas.chat.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Starts a second, independent instance of the application sharing MongoDB and Redis with the
 * test's instance, then proves a message sent on instance A reaches a user connected to instance B.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MultiInstanceFanoutIntegrationTest extends IntegrationTest {

    @LocalServerPort
    private int portA;

    @Autowired
    private MongoDBContainer mongo;

    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redis;

    @Autowired
    private JwtDecoder jwtDecoder;

    /** Instance B must use exactly the same database as this instance. */
    @Autowired
    private MongoTemplate mongoTemplate;

    private ConfigurableApplicationContext instanceB;

    @BeforeEach
    void startSecondInstance() {
        // Command-line args, not builder .properties(): those are defaults that application.yml would override
        instanceB = new SpringApplicationBuilder(RealtimeChatApplication.class).run(
                "--server.port=0",
                "--spring.data.mongodb.uri=" + mongo.getReplicaSetUrl(mongoTemplate.getDb().getName()),
                "--spring.data.redis.host=" + redis.getHost(),
                "--spring.data.redis.port=" + redis.getMappedPort(6379));
    }

    @AfterEach
    void stopSecondInstance() {
        instanceB.close();
    }

    @Test
    void messageSentOnOneInstanceReachesSubscribersOnAnother() throws Exception {
        String aliceEmail = uniqueEmail();
        String bobEmail = uniqueEmail();
        register(aliceEmail);
        register(bobEmail);
        String alice = "Bearer " + login(aliceEmail);
        String bob = "Bearer " + login(bobEmail);
        String roomId = new ChatTestClient(mockMvc, objectMapper).createRoom(alice, "Scaled", List.of(bobEmail));
        int portB = ((WebServerApplicationContext) instanceB).getWebServer().getPort();

        try (StompTestClient aliceOnA = StompTestClient.connect(portA, alice);
             StompTestClient bobOnB = StompTestClient.connect(portB, bob)) {
            BlockingQueue<MessageResponse> bobInbox = bobOnB.subscribe("/topic/rooms/" + roomId, MessageResponse.class);
            awaitSubscribed(instanceB.getBean(SimpUserRegistry.class), bob, roomId);

            aliceOnA.send("/app/rooms/" + roomId + "/send", new SendMessageRequest("hello from instance A"));

            MessageResponse received = bobInbox.poll(10, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.content()).isEqualTo("hello from instance A");
            assertThat(portA).isNotEqualTo(portB);
        }
    }

    private void awaitSubscribed(SimpUserRegistry registry, String token, String roomId) {
        String userId = jwtDecoder.decode(token.substring("Bearer ".length())).getSubject();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                !registry.findSubscriptions(s -> s.getSession().getUser().getName().equals(userId)
                        && s.getDestination().equals("/topic/rooms/" + roomId)).isEmpty());
    }
}
