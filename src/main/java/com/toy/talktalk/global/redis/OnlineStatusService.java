package com.toy.talktalk.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnlineStatusService {

    private static final String ONLINE_USERS_KEY = "online:users";

    private final RedisTemplate<String, String> redisTemplate;

    public void markOnline(Long userId) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, String.valueOf(userId));
    }

    public void markOffline(Long userId) {
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, String.valueOf(userId));
    }

    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, String.valueOf(userId)));
    }

    public Set<String> getOnlineUserIds() {
        return redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
    }
}
