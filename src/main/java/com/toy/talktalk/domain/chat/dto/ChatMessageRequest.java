package com.toy.talktalk.domain.chat.dto;

import com.toy.talktalk.domain.chat.entity.MessageType;

public record ChatMessageRequest(
        Long roomId,
        String content,
        MessageType type
) {
}
