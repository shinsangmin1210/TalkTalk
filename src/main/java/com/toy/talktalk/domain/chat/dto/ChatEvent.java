package com.toy.talktalk.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatMessageResponse.class, name = "MESSAGE"),
        @JsonSubTypes.Type(value = ReadAckResponse.class, name = "READ_ACK")
})
public interface ChatEvent {
}
