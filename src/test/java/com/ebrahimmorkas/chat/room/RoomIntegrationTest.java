package com.ebrahimmorkas.chat.room;

import com.ebrahimmorkas.chat.support.ChatTestClient;
import com.ebrahimmorkas.chat.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomIntegrationTest extends IntegrationTest {

    private ChatTestClient chat;
    private String aliceEmail;
    private String bobEmail;
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        chat = new ChatTestClient(mockMvc, objectMapper);
        aliceEmail = uniqueEmail();
        bobEmail = uniqueEmail();
        register(aliceEmail);
        register(bobEmail);
        alice = "Bearer " + login(aliceEmail);
        bob = "Bearer " + login(bobEmail);
    }

    @Test
    void creatorAndInvitedMembersSeeTheRoom() throws Exception {
        String roomId = chat.createRoom(alice, "Team", List.of(bobEmail));

        mockMvc.perform(get("/api/rooms/{id}", roomId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds", hasSize(2)));
        mockMvc.perform(get("/api/rooms").header("Authorization", bob))
                .andExpect(jsonPath("$[0].id").value(roomId));
    }

    @Test
    void nonMembersGet404() throws Exception {
        String roomId = chat.createRoom(alice, "Private", List.of());

        mockMvc.perform(get("/api/rooms/{id}", roomId).header("Authorization", bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/rooms/{id}/members", roomId).header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"%s\"}".formatted(bobEmail)))
                .andExpect(status().isNotFound());
    }

    @Test
    void membersCanInviteAndAddingTwiceIsIdempotent() throws Exception {
        String roomId = chat.createRoom(alice, "Growing", List.of());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/rooms/{id}/members", roomId).header("Authorization", alice)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"%s\"}".formatted(bobEmail)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberIds", hasSize(2)));
        }
    }

    @Test
    void membersCanLeaveButOwnerCannot() throws Exception {
        String roomId = chat.createRoom(alice, "Temp", List.of(bobEmail));

        mockMvc.perform(delete("/api/rooms/{id}/members/me", roomId).header("Authorization", bob))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rooms/{id}", roomId).header("Authorization", bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/rooms/{id}/members/me", roomId).header("Authorization", alice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
    }

    @Test
    void invitingUnknownUserReturns404() throws Exception {
        mockMvc.perform(post("/api/rooms").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\", \"memberEmails\": [\"ghost@example.com\"]}"))
                .andExpect(status().isNotFound());
    }
}
