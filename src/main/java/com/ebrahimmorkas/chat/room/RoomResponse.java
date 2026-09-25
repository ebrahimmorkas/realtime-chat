package com.ebrahimmorkas.chat.room;

import java.time.Instant;
import java.util.Set;

public record RoomResponse(String id, String name, String ownerId, Set<String> memberIds, Instant createdAt) {

    static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getOwnerId(), room.getMemberIds(), room.getCreatedAt());
    }
}
