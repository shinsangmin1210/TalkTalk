package com.toy.talktalk.domain.chat.dto;

import com.toy.talktalk.domain.chat.entity.Message;
import com.toy.talktalk.domain.chat.entity.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderNickname,
        String content,
        MessageType type,
        LocalDateTime sentAt
) implements ChatEvent {
    public static ChatMessageResponse from(Message message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                message.getSender() != null ? message.getSender().getId() : null,
                message.getSender() != null ? message.getSender().getNickname() : null,
                message.getContent(),
                message.getType(),
                message.getSentAt()
        );
    }
}
