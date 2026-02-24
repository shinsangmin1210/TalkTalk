package com.toy.talktalk.domain.chat.service;

import com.toy.talktalk.domain.chat.dto.ChatMessageRequest;
import com.toy.talktalk.domain.chat.dto.ChatMessageResponse;
import com.toy.talktalk.domain.chat.dto.MessagePageResponse;
import com.toy.talktalk.domain.chat.entity.ChatRoom;
import com.toy.talktalk.domain.chat.entity.Message;
import com.toy.talktalk.domain.chat.repository.ChatRoomMemberRepository;
import com.toy.talktalk.domain.chat.repository.ChatRoomRepository;
import com.toy.talktalk.domain.chat.repository.MessageRepository;
import com.toy.talktalk.domain.user.entity.User;
import com.toy.talktalk.domain.user.repository.UserRepository;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import com.toy.talktalk.domain.chat.dto.ReadAckResponse;
import com.toy.talktalk.global.redis.RedisChatPublisher;
import com.toy.talktalk.global.redis.RedisSubscriptionManager;
import com.toy.talktalk.global.redis.UnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatMessageService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final UnreadCountService unreadCountService;
    private final RedisChatPublisher redisChatPublisher;
    private final RedisSubscriptionManager redisSubscriptionManager;

    @Transactional
    public ChatMessageResponse saveMessage(Long senderId, ChatMessageRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findById(request.roomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, sender)) {
            throw new BusinessException(ErrorCode.NOT_ROOM_MEMBER);
        }

        Message message = Message.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(request.content())
                .type(request.type())
                .build();

        ChatMessageResponse response = ChatMessageResponse.from(messageRepository.save(message));

        List<Long> memberIds = chatRoomMemberRepository.findAllByChatRoom(chatRoom).stream()
                .map(member -> member.getUser().getId())
                .toList();
        unreadCountService.incrementUnread(request.roomId(), senderId, memberIds);

        return response;
    }

    public MessagePageResponse getMessages(Long userId, Long roomId, Long cursor, int limit) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new BusinessException(ErrorCode.NOT_ROOM_MEMBER);
        }

        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<ChatMessageResponse> messages = (cursor == null
                ? messageRepository.findByChatRoomIdOrderByIdDesc(roomId, pageRequest)
                : messageRepository.findByChatRoomIdBeforeCursor(roomId, cursor, pageRequest))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();

        return MessagePageResponse.of(messages, limit);
    }

    public void markAsRead(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new BusinessException(ErrorCode.NOT_ROOM_MEMBER);
        }

        unreadCountService.resetUnread(roomId, userId);

        redisSubscriptionManager.subscribeRoom(roomId);
        redisChatPublisher.publish(roomId, ReadAckResponse.of(roomId, userId));
    }

    @Transactional
    public ChatMessageResponse saveSystemMessage(Long roomId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        Message message = Message.ofSystem(chatRoom, content);
        return ChatMessageResponse.from(messageRepository.save(message));
    }
}
