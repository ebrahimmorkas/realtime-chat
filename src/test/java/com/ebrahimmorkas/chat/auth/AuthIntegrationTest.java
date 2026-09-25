package com.ebrahimmorkas.chat.auth;

import com.ebrahimmorkas.chat.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registeredUserCanLoginAndAccessProtectedEndpoint() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))

                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void emailIsCaseInsensitiveAndUnique() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Another123", "displayName": "Dup"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "WrongPass1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void unknownEmailGetsTheSameErrorAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "Whatever1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "short", "displayName": "Weak"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = newUserToken();

        mockMvc.perform(get("/api/users/me").header("Authorization", token + "x"))
                .andExpect(status().isUnauthorized());
    }
}
