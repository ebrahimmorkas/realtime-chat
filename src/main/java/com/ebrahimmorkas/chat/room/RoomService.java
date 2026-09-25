package com.ebrahimmorkas.chat.room;

import com.ebrahimmorkas.chat.common.BusinessRuleException;
import com.ebrahimmorkas.chat.common.NotFoundException;
import com.ebrahimmorkas.chat.user.User;
import com.ebrahimmorkas.chat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public RoomResponse create(String userId, CreateRoomRequest request) {
        Set<String> members = new HashSet<>();
        if (request.memberEmails() != null) {
            request.memberEmails().forEach(email -> members.add(userIdByEmail(email)));
        }
        return RoomResponse.from(roomRepository.save(new Room(request.name().trim(), userId, members)));
    }

    public List<RoomResponse> findMine(String userId) {
        return roomRepository.findByMemberIdsContainingOrderByCreatedAtDesc(userId).stream()
                .map(RoomResponse::from)
                .toList();
    }

    public RoomResponse findOne(String userId, String roomId) {
        return RoomResponse.from(requireMembership(userId, roomId));
    }

    /**
     * Adds a member with an atomic {@code $addToSet} guarded by "caller is a member" in the same
     * filter, so concurrent joins never overwrite each other and non-members can't add anyone.
     */
    public RoomResponse addMember(String userId, String roomId, String email) {
        String newMemberId = userIdByEmail(email);
        Room updated = mongoTemplate.findAndModify(
                memberQuery(roomId, userId),
                new Update().addToSet("memberIds", newMemberId),
                FindAndModifyOptions.options().returnNew(true),
                Room.class);
        if (updated == null) {
            throw roomNotFound(roomId);
        }
        return RoomResponse.from(updated);
    }

    public void leave(String userId, String roomId) {
        Room room = requireMembership(userId, roomId);
        if (room.getOwnerId().equals(userId)) {
            throw new BusinessRuleException("OWNER_CANNOT_LEAVE", "The room owner cannot leave the room");
        }
        mongoTemplate.updateFirst(memberQuery(roomId, userId), new Update().pull("memberIds", userId), Room.class);
    }

    /** Non-members get 404, not 403, so room ids cannot be probed. */
    public Room requireMembership(String userId, String roomId) {
        return roomRepository.findByIdAndMemberIdsContaining(roomId, userId).orElseThrow(() -> roomNotFound(roomId));
    }

    public boolean isMember(String userId, String roomId) {
        return roomRepository.existsByIdAndMemberIdsContaining(roomId, userId);
    }

    private String userIdByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(User::getId)
                .orElseThrow(() -> new NotFoundException("No user with email " + email));
    }

    private static Query memberQuery(String roomId, String userId) {
        return Query.query(Criteria.where("_id").is(roomId).and("memberIds").is(userId));
    }

    private static NotFoundException roomNotFound(String roomId) {
        return new NotFoundException("Room " + roomId + " not found");
    }
}
