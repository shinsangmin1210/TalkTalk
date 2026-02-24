package com.toy.talktalk.domain.chat.dto;

import com.toy.talktalk.domain.chat.entity.ChatRoom;
import com.toy.talktalk.domain.chat.entity.ChatRoomType;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        String name,
        ChatRoomType type,
        int memberCount,
        LocalDateTime createdAt
) {
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getName(),
                chatRoom.getType(),
                chatRoom.getMembers().size(),
                chatRoom.getCreatedAt()
        );
    }
}
