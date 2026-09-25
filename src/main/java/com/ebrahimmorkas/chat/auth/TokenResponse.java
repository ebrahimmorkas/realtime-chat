package com.ebrahimmorkas.chat.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String token, long expiresInSeconds) {
        return new TokenResponse(token, "Bearer", expiresInSeconds);
    }
}
