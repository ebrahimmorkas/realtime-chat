package com.ebrahimmorkas.chat.room;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends MongoRepository<Room, String> {

    List<Room> findByMemberIdsContainingOrderByCreatedAtDesc(String userId);

    Optional<Room> findByIdAndMemberIdsContaining(String id, String userId);

    boolean existsByIdAndMemberIdsContaining(String id, String userId);
}
