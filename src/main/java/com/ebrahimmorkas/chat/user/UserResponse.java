package com.ebrahimmorkas.chat.user;

import java.time.Instant;

public record UserResponse(String id, String email, String displayName, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
