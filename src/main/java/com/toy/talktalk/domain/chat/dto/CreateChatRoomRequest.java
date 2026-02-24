package com.toy.talktalk.domain.chat.dto;

import com.toy.talktalk.domain.chat.entity.ChatRoomType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateChatRoomRequest(
        @NotNull ChatRoomType type,
        String name,
        @NotNull @Size(min = 1) List<Long> inviteeIds
) {
}
