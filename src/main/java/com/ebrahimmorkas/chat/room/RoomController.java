package com.ebrahimmorkas.chat.room;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room; the creator becomes its owner")
    public RoomResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRoomRequest request) {
        return roomService.create(jwt.getSubject(), request);
    }

    @GetMapping
    @Operation(summary = "Rooms I'm a member of, newest first")
    public List<RoomResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return roomService.findMine(jwt.getSubject());
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get a room I'm a member of")
    public RoomResponse findOne(@AuthenticationPrincipal Jwt jwt, @PathVariable String roomId) {
        return roomService.findOne(jwt.getSubject(), roomId);
    }

    @PostMapping("/{roomId}/members")
    @Operation(summary = "Add a user to a room (any member can invite)")
    public RoomResponse addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable String roomId,
                                  @Valid @RequestBody AddMemberRequest request) {
        return roomService.addMember(jwt.getSubject(), roomId, request.email());
    }

    @DeleteMapping("/{roomId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave a room")
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String roomId) {
        roomService.leave(jwt.getSubject(), roomId);
    }
}
