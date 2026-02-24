package com.toy.talktalk.domain.chat.controller;

import com.toy.talktalk.domain.chat.dto.ChatMessageRequest;
import com.toy.talktalk.domain.chat.dto.ChatMessageResponse;
import com.toy.talktalk.domain.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessageRequest request, Principal principal) {
        Long senderId = Long.parseLong(principal.getName());
        ChatMessageResponse response = chatMessageService.saveMessage(senderId, request);
        messagingTemplate.convertAndSend("/sub/room/" + request.roomId(), response);
    }
}
