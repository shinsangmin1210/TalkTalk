package com.toy.talktalk.domain.chat.dto;

import java.util.List;

public record MessagePageResponse(
        List<ChatMessageResponse> messages,
        boolean hasNext,
        Long nextCursor
) {
    public static MessagePageResponse of(List<ChatMessageResponse> messages, int limit) {
        boolean hasNext = messages.size() > limit;
        List<ChatMessageResponse> result = hasNext ? messages.subList(0, limit) : messages;
        Long nextCursor = hasNext ? result.get(result.size() - 1).messageId() : null;
        return new MessagePageResponse(result, hasNext, nextCursor);
    }
}
