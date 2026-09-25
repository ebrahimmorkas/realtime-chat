package com.ebrahimmorkas.chat.room;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoomRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 100) List<@Email String> memberEmails) {
}
