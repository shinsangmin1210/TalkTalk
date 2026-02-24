package com.toy.talktalk.global.redis;

import com.toy.talktalk.domain.chat.dto.ChatEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisChatPublisher {

    private static final String CHAT_TOPIC_PREFIX = "chat:room:";

    private final RedisTemplate<String, Object> objectRedisTemplate;

    public void publish(Long roomId, ChatEvent event) {
        objectRedisTemplate.convertAndSend(CHAT_TOPIC_PREFIX + roomId, event);
    }
}
