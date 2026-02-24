package com.toy.talktalk.domain.chat.controller;

import com.toy.talktalk.domain.chat.dto.ChatMessageRequest;
import com.toy.talktalk.domain.chat.dto.ChatMessageResponse;
import com.toy.talktalk.domain.chat.service.ChatMessageService;
import com.toy.talktalk.global.redis.RedisChatPublisher;
import com.toy.talktalk.global.redis.RedisSubscriptionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final ChatMessageService chatMessageService;
    private final RedisChatPublisher redisChatPublisher;
    private final RedisSubscriptionManager redisSubscriptionManager;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessageRequest request, Principal principal) {
        Long senderId = Long.parseLong(principal.getName());
        ChatMessageResponse response = chatMessageService.saveMessage(senderId, request);

        redisSubscriptionManager.subscribeRoom(request.roomId());
        redisChatPublisher.publish(response);
    }
}
