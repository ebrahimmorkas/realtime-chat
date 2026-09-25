package com.ebrahimmorkas.chat.message;

import com.ebrahimmorkas.chat.support.ChatTestClient;
import com.ebrahimmorkas.chat.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageHistoryIntegrationTest extends IntegrationTest {

    @Autowired
    private ChatMessageRepository messageRepository;

    private String alice;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        String email = uniqueEmail();
        register(email);
        alice = "Bearer " + login(email);
        roomId = new ChatTestClient(mockMvc, objectMapper).createRoom(alice, "History", List.of());
    }

    @Test
    void pagesThroughHistoryNewestFirstWithoutGapsOrDuplicates() throws Exception {
        for (int i = 1; i <= 120; i++) {
            messageRepository.save(new ChatMessage(roomId, "u", "Alice", "message " + i, Instant.now()));
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get("/api/rooms/{id}/messages", roomId)
                    .header("Authorization", alice).param("limit", "50");
            if (cursor != null) {
                request.param("before", cursor);
            }
            JsonNode page = json(mockMvc.perform(request).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            page.get("messages").forEach(m -> seen.add(m.get("content").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(120);
        assertThat(seen.getFirst()).isEqualTo("message 120");
        assertThat(seen.getLast()).isEqualTo("message 1");
        Set<String> unique = new HashSet<>(seen);
        assertThat(unique).hasSize(120);
    }

    @Test
    void newMessagesDoNotShiftOlderPages() throws Exception {
        for (int i = 1; i <= 10; i++) {
            messageRepository.save(new ChatMessage(roomId, "u", "Alice", "old " + i, Instant.now()));
        }
        JsonNode first = json(mockMvc.perform(get("/api/rooms/{id}/messages", roomId)
                        .header("Authorization", alice).param("limit", "5"))
                .andReturn().getResponse().getContentAsString());

        messageRepository.save(new ChatMessage(roomId, "u", "Alice", "brand new", Instant.now()));

        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header("Authorization", alice)
                        .param("limit", "5").param("before", first.get("nextCursor").asText()))
                .andExpect(jsonPath("$.messages[0].content").value("old 5"))
                .andExpect(jsonPath("$.messages[4].content").value("old 1"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void invalidCursorIsRejected() throws Exception {
        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header("Authorization", alice).param("before", "nope"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void nonMembersCannotReadHistory() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header("Authorization", "Bearer " + login(email)))
                .andExpect(status().isNotFound());
    }
}
