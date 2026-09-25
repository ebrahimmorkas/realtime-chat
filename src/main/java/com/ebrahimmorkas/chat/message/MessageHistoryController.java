package com.ebrahimmorkas.chat.message;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Messages")
@SecurityRequirement(name = "bearerAuth")
public class MessageHistoryController {

    private final MessageHistoryService historyService;

    @GetMapping("/api/rooms/{roomId}/messages")
    @Operation(summary = "Message history, newest first, with cursor pagination")
    public MessagePage history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @Parameter(description = "Return messages older than this message id (the previous page's nextCursor)")
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit) {
        return historyService.history(jwt.getSubject(), roomId, before, limit);
    }
}
