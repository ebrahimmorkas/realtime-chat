package com.ebrahimmorkas.chat.presence;

import com.ebrahimmorkas.chat.room.Room;
import com.ebrahimmorkas.chat.room.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@Tag(name = "Presence")
@SecurityRequirement(name = "bearerAuth")
public class PresenceController {

    private final RoomService roomService;
    private final PresenceTracker presenceTracker;

    @GetMapping("/api/rooms/{roomId}/presence")
    @Operation(summary = "Room members that are currently connected (on any instance)")
    public PresenceResponse presence(@AuthenticationPrincipal Jwt jwt, @PathVariable String roomId) {
        Room room = roomService.requireMembership(jwt.getSubject(), roomId);
        return new PresenceResponse(presenceTracker.online(room.getMemberIds()));
    }

    public record PresenceResponse(Set<String> online) {
    }
}
