package com.toy.talktalk.global.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.talktalk.domain.chat.dto.ChatEvent;
import com.toy.talktalk.domain.chat.dto.ChatMessageResponse;
import com.toy.talktalk.domain.chat.dto.ReadAckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatSubscriber implements MessageListener {

    private static final String STOMP_TOPIC_PREFIX = "/sub/room/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatEvent event = objectMapper.readValue(message.getBody(), ChatEvent.class);

            if (event instanceof ChatMessageResponse chatMessage) {
                messagingTemplate.convertAndSend(STOMP_TOPIC_PREFIX + chatMessage.roomId(), chatMessage);
            } else if (event instanceof ReadAckResponse readAck) {
                messagingTemplate.convertAndSend(STOMP_TOPIC_PREFIX + readAck.roomId(), readAck);
            }
        } catch (Exception e) {
            log.error("Redis 메시지 역직렬화 실패: {}", e.getMessage());
        }
    }
}
