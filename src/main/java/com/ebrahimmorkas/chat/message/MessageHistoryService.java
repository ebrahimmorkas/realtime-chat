package com.ebrahimmorkas.chat.message;

import com.ebrahimmorkas.chat.common.BusinessRuleException;
import com.ebrahimmorkas.chat.room.RoomService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cursor (keyset) pagination over the (roomId, _id) index.
 *
 * <p>Offset pagination ({@code skip(n)}) gets slower the deeper you scroll and shows duplicates
 * or gaps when new messages arrive between page loads. Seeking with {@code _id < cursor} is an
 * index range scan whatever the depth, and new messages never shift older pages.
 */
@Service
@RequiredArgsConstructor
public class MessageHistoryService {

    static final int MAX_PAGE_SIZE = 100;

    private final MongoTemplate mongoTemplate;
    private final RoomService roomService;

    public MessagePage history(String userId, String roomId, String before, int limit) {
        roomService.requireMembership(userId, roomId);
        int pageSize = Math.clamp(limit, 1, MAX_PAGE_SIZE);

        Criteria criteria = Criteria.where("roomId").is(roomId);
        if (before != null) {
            if (!ObjectId.isValid(before)) {
                throw new BusinessRuleException("INVALID_CURSOR", "Cursor '%s' is not valid".formatted(before));
            }
            criteria = criteria.and("_id").lt(new ObjectId(before));
        }
        // Fetch one extra row to learn whether an older page exists without a count query
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "_id")).limit(pageSize + 1);
        List<ChatMessage> rows = mongoTemplate.find(query, ChatMessage.class);

        boolean hasMore = rows.size() > pageSize;
        List<MessageResponse> page = rows.stream().limit(pageSize).map(MessageResponse::from).toList();
        return new MessagePage(page, hasMore ? page.getLast().id() : null);
    }
}
