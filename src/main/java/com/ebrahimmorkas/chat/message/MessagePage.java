package com.ebrahimmorkas.chat.message;

import java.util.List;

/**
 * One page of history, newest first. Pass {@code nextCursor} as {@code before} to load older
 * messages; it is {@code null} when there is nothing older.
 */
public record MessagePage(List<MessageResponse> messages, String nextCursor) {
}
