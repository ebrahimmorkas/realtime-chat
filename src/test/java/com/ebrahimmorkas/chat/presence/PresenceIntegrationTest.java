package com.ebrahimmorkas.chat.presence;

import com.ebrahimmorkas.chat.support.ChatTestClient;
import com.ebrahimmorkas.chat.support.IntegrationTest;
import com.ebrahimmorkas.chat.support.StompTestClient;
import com.ebrahimmorkas.chat.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PresenceIntegrationTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PresenceTracker presenceTracker;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void membersAppearOnlineWhileConnectedAndOfflineAfterAllTabsClose() throws Exception {
        String aliceEmail = uniqueEmail();
        String bobEmail = uniqueEmail();
        register(aliceEmail);
        register(bobEmail);
        String alice = "Bearer " + login(aliceEmail);
        String bob = "Bearer " + login(bobEmail);
        String roomId = new ChatTestClient(mockMvc, objectMapper).createRoom(alice, "Presence", List.of(bobEmail));
        String bobId = jwtDecoder.decode(bob.substring(7)).getSubject();

        try (StompTestClient tab1 = StompTestClient.connect(port, bob)) {
            StompTestClient tab2 = StompTestClient.connect(port, bob);
            await().atMost(Duration.ofSeconds(10)).until(() -> onlineIn(roomId, alice).contains(bobId));

            tab2.close();
            // Still connected in the other tab
            Thread.sleep(500);
            assertThat(onlineIn(roomId, alice)).contains(bobId);
        }

        await().atMost(Duration.ofSeconds(10)).until(() -> !onlineIn(roomId, alice).contains(bobId));
    }

    @Test
    void usersOfACrashedInstanceStopAppearingOnline() {
        String deadInstance = "crashed-" + System.nanoTime();
        redis.opsForHash().put(PresenceTracker.INSTANCE_KEY_PREFIX + deadInstance, "ghost-user", "1");
        redis.expire(PresenceTracker.INSTANCE_KEY_PREFIX + deadInstance, Duration.ofSeconds(1));
        redis.opsForSet().add(PresenceTracker.INSTANCES_KEY, deadInstance);
        assertThat(presenceTracker.online(Set.of("ghost-user"))).containsExactly("ghost-user");

        // The crashed instance never refreshes its TTL
        await().atMost(Duration.ofSeconds(5)).until(() -> presenceTracker.online(Set.of("ghost-user")).isEmpty());
        assertThat(redis.opsForSet().isMember(PresenceTracker.INSTANCES_KEY, deadInstance)).isFalse();
    }

    private Set<String> onlineIn(String roomId, String token) throws Exception {
        String body = mockMvc.perform(get("/api/rooms/{id}/presence", roomId).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        Set<String> online = new HashSet<>();
        json(body).get("online").forEach(n -> online.add(n.asText()));
        return online;
    }
}
