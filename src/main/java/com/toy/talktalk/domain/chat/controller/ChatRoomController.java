package com.toy.talktalk.domain.chat.controller;

import com.toy.talktalk.domain.chat.dto.ChatRoomResponse;
import com.toy.talktalk.domain.chat.dto.CreateChatRoomRequest;
import com.toy.talktalk.domain.chat.dto.InviteMemberRequest;
import com.toy.talktalk.domain.chat.dto.MessagePageResponse;
import com.toy.talktalk.domain.chat.service.ChatMessageService;
import com.toy.talktalk.domain.chat.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateChatRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatRoomService.createChatRoom(userId, request));
    }

    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<Void> leaveChatRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        chatRoomService.leaveChatRoom(userId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/members")
    public ResponseEntity<Void> inviteMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid InviteMemberRequest request
    ) {
        chatRoomService.inviteMember(userId, roomId, request.inviteeId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(chatRoomService.getChatRoom(userId, roomId));
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyChatRooms(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(chatRoomService.getMyChatRooms(userId));
    }

    @PostMapping("/{roomId}/messages/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        chatMessageService.markAsRead(userId, roomId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<MessagePageResponse> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(chatMessageService.getMessages(userId, roomId, cursor, limit));
    }
}
