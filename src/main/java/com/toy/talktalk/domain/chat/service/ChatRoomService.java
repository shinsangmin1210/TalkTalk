package com.toy.talktalk.domain.chat.service;

import com.toy.talktalk.domain.chat.dto.ChatRoomResponse;
import com.toy.talktalk.domain.chat.dto.CreateChatRoomRequest;
import com.toy.talktalk.domain.chat.entity.ChatRoom;
import com.toy.talktalk.domain.chat.entity.ChatRoomMember;
import com.toy.talktalk.domain.chat.entity.ChatRoomType;
import com.toy.talktalk.domain.chat.repository.ChatRoomMemberRepository;
import com.toy.talktalk.domain.chat.repository.ChatRoomRepository;
import com.toy.talktalk.domain.user.entity.User;
import com.toy.talktalk.domain.user.repository.UserRepository;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import com.toy.talktalk.global.redis.UnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final UnreadCountService unreadCountService;

    @Transactional
    public ChatRoomResponse createChatRoom(Long creatorId, CreateChatRoomRequest request) {
        validateCreateRequest(request);

        User creator = findUserById(creatorId);

        ChatRoom chatRoom = ChatRoom.builder()
                .name(request.name())
                .type(request.type())
                .build();
        chatRoomRepository.save(chatRoom);

        addMember(chatRoom, creator);
        for (Long inviteeId : request.inviteeIds()) {
            User invitee = findUserById(inviteeId);
            addMember(chatRoom, invitee);
        }

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public void leaveChatRoom(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = findUserById(userId);
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomAndUser(chatRoom, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ROOM_MEMBER));

        chatRoomMemberRepository.delete(member);
    }

    @Transactional
    public void inviteMember(Long userId, Long roomId, Long inviteeId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        boolean isRequesterMember = chatRoomMemberRepository.existsByChatRoomAndUser(
                chatRoom, findUserById(userId));
        if (!isRequesterMember) {
            throw new BusinessException(ErrorCode.NOT_ROOM_MEMBER);
        }

        User invitee = findUserById(inviteeId);
        if (chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, invitee)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED_ROOM);
        }

        addMember(chatRoom, invitee);
    }

    public ChatRoomResponse getChatRoom(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        boolean isMember = chatRoomMemberRepository.existsByChatRoomAndUser(
                chatRoom, findUserById(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.NOT_ROOM_MEMBER);
        }

        return ChatRoomResponse.from(chatRoom);
    }

    public List<ChatRoomResponse> getMyChatRooms(Long userId) {
        return chatRoomRepository.findAllByUserId(userId).stream()
                .map(room -> ChatRoomResponse.from(room, unreadCountService.getUnreadCount(room.getId(), userId)))
                .toList();
    }

    private void validateCreateRequest(CreateChatRoomRequest request) {
        if (request.type() == ChatRoomType.GROUP) {
            if (request.name() == null || request.name().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        if (request.type() == ChatRoomType.DIRECT) {
            if (request.inviteeIds().size() != 1) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    private void addMember(ChatRoom chatRoom, User user) {
        ChatRoomMember member = ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .user(user)
                .build();
        chatRoomMemberRepository.save(member);
        chatRoom.getMembers().add(member);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
