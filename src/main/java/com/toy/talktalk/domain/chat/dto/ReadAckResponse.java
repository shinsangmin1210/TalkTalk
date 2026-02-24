package com.toy.talktalk.domain.chat.dto;

import java.time.LocalDateTime;

public record ReadAckResponse(
        Long roomId,
        Long userId,
        LocalDateTime readAt
) implements ChatEvent {

    public static ReadAckResponse of(Long roomId, Long userId) {
        return new ReadAckResponse(roomId, userId, LocalDateTime.now());
    }
}
