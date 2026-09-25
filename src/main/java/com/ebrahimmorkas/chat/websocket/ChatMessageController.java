package com.ebrahimmorkas.chat.websocket;

import com.ebrahimmorkas.chat.common.BusinessRuleException;
import com.ebrahimmorkas.chat.common.NotFoundException;
import com.ebrahimmorkas.chat.message.MessageService;
import com.ebrahimmorkas.chat.message.SendMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

/**
 * STOMP endpoints. Clients SEND to {@code /app/rooms/{roomId}/send} and SUBSCRIBE to
 * {@code /topic/rooms/{roomId}}; errors come back privately on {@code /user/queue/errors}.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final MessageService messageService;

    @MessageMapping("/rooms/{roomId}/send")
    public void send(@DestinationVariable String roomId, @Valid @Payload SendMessageRequest request,
                     JwtAuthenticationToken principal) {
        messageService.send(roomId, principal.getName(), principal.getToken().getClaimAsString("name"),
                request.content());
    }

    @MessageExceptionHandler({NotFoundException.class, BusinessRuleException.class})
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatError handleDomainError(RuntimeException ex) {
        return new ChatError("REJECTED", ex.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatError handleInvalidMessage(MethodArgumentNotValidException ex) {
        return new ChatError("INVALID_MESSAGE", "Message must be 1-2000 characters");
    }

    public record ChatError(String code, String message) {
    }
}
