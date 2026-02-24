package com.toy.talktalk.global.websocket;

import com.toy.talktalk.domain.chat.dto.ChatMessageResponse;
import com.toy.talktalk.domain.chat.service.ChatMessageService;
import com.toy.talktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompEventListener {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/room/";

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();

        if (destination == null || principal == null) {
            return;
        }

        if (destination.startsWith(ROOM_DESTINATION_PREFIX)) {
            Long roomId = extractRoomId(destination);
            Long userId = Long.parseLong(principal.getName());
            String nickname = userRepository.findById(userId)
                    .map(user -> user.getNickname())
                    .orElse("알 수 없음");

            ChatMessageResponse systemMessage =
                    chatMessageService.saveSystemMessage(roomId, nickname + "님이 입장했습니다.");
            messagingTemplate.convertAndSend(destination, systemMessage);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();

        if (principal == null) {
            return;
        }

        Long userId = Long.parseLong(principal.getName());
        String nickname = userRepository.findById(userId)
                .map(user -> user.getNickname())
                .orElse("알 수 없음");

        log.debug("WebSocket disconnected: userId={}, nickname={}", userId, nickname);
    }

    private Long extractRoomId(String destination) {
        return Long.parseLong(destination.substring(ROOM_DESTINATION_PREFIX.length()));
    }
}
