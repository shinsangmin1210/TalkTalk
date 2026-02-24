package com.toy.talktalk.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private static final String UNREAD_KEY_PREFIX = "unread:";

    private final RedisTemplate<String, String> redisTemplate;

    // 채팅방의 발신자를 제외한 모든 멤버 unread +1
    public void incrementUnread(Long roomId, Long senderId, List<Long> memberIds) {
        String key = UNREAD_KEY_PREFIX + roomId;
        memberIds.stream()
                .filter(memberId -> !memberId.equals(senderId))
                .forEach(memberId ->
                        redisTemplate.opsForHash().increment(key, String.valueOf(memberId), 1));
    }

    // 채팅방 입장 시 해당 유저 unread 초기화
    public void resetUnread(Long roomId, Long userId) {
        String key = UNREAD_KEY_PREFIX + roomId;
        redisTemplate.opsForHash().delete(key, String.valueOf(userId));
    }

    // 특정 유저의 특정 채팅방 unread 수 조회
    public long getUnreadCount(Long roomId, Long userId) {
        String key = UNREAD_KEY_PREFIX + roomId;
        Object value = redisTemplate.opsForHash().get(key, String.valueOf(userId));
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
