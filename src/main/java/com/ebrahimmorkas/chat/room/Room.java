package com.ebrahimmorkas.chat.room;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document("rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    private String id;

    private String name;

    private String ownerId;

    /** Multikey index: "rooms I belong to" is the most frequent query. */
    @Indexed
    private Set<String> memberIds = new HashSet<>();

    @CreatedDate
    private Instant createdAt;

    public Room(String name, String ownerId, Set<String> memberIds) {
        this.name = name;
        this.ownerId = ownerId;
        this.memberIds = new HashSet<>(memberIds);
        this.memberIds.add(ownerId);
    }

    public boolean hasMember(String userId) {
        return memberIds.contains(userId);
    }
}
