package com.toy.talktalk.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RedisSubscriptionManager {

    private static final String CHAT_TOPIC_PREFIX = "chat:room:";

    private final RedisMessageListenerContainer listenerContainer;
    private final RedisChatSubscriber redisChatSubscriber;

    private final Map<Long, ChannelTopic> subscribedRooms = new ConcurrentHashMap<>();

    public void subscribeRoom(Long roomId) {
        if (!subscribedRooms.containsKey(roomId)) {
            ChannelTopic topic = new ChannelTopic(CHAT_TOPIC_PREFIX + roomId);
            listenerContainer.addMessageListener(redisChatSubscriber, topic);
            subscribedRooms.put(roomId, topic);
        }
    }
}
