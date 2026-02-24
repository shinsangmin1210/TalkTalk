package com.toy.talktalk.domain.chat.repository;

import com.toy.talktalk.domain.chat.entity.ChatRoom;
import com.toy.talktalk.domain.chat.entity.ChatRoomMember;
import com.toy.talktalk.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoomAndUser(ChatRoom chatRoom, User user);

    Optional<ChatRoomMember> findByChatRoomAndUser(ChatRoom chatRoom, User user);

    List<ChatRoomMember> findAllByChatRoom(ChatRoom chatRoom);
}
