package com.ebrahimmorkas.chat.message;

/** Delivers a persisted message to everyone currently subscribed to its room. */
public interface MessageBroadcaster {

    static String roomTopic(String roomId) {
        return "/topic/rooms/" + roomId;
    }

    void broadcast(MessageResponse message);
}
