package com.ebrahimmorkas.chat.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for setting up rooms in integration tests. */
public record ChatTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {

    public String createRoom(String token, String name, List<String> memberEmails) throws Exception {
        String members = memberEmails.stream().map(e -> "\"" + e + "\"").collect(Collectors.joining(","));
        String body = mockMvc.perform(post("/api/rooms").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"memberEmails\": [%s]}".formatted(name, members)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
