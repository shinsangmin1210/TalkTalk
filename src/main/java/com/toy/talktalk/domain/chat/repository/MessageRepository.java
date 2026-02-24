package com.toy.talktalk.domain.chat.repository;

import com.toy.talktalk.domain.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 커서 기반 페이지네이션 — cursor(messageId) 이전 메시지를 최신순으로 조회
    @Query("SELECT m FROM Message m WHERE m.chatRoom.id = :roomId AND m.id < :cursor ORDER BY m.id DESC")
    List<Message> findByChatRoomIdBeforeCursor(
            @Param("roomId") Long roomId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 첫 조회 (cursor 없을 때)
    @Query("SELECT m FROM Message m WHERE m.chatRoom.id = :roomId ORDER BY m.id DESC")
    List<Message> findByChatRoomIdOrderByIdDesc(
            @Param("roomId") Long roomId,
            Pageable pageable);
}
