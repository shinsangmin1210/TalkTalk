package com.toy.talktalk.global.redis;

import com.toy.talktalk.domain.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisChatPublisher {

    private static final String CHAT_TOPIC_PREFIX = "chat:room:";

    private final RedisTemplate<String, Object> objectRedisTemplate;

    public void publish(ChatMessageResponse message) {
        String channel = CHAT_TOPIC_PREFIX + message.roomId();
        objectRedisTemplate.convertAndSend(channel, message);
    }
}
