package com.toy.talktalk.domain.chat.entity;

import com.toy.talktalk.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime sentAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean isRead = false;

    public static Message ofSystem(ChatRoom chatRoom, String content) {
        return Message.builder()
                .chatRoom(chatRoom)
                .sender(null)
                .content(content)
                .type(MessageType.SYSTEM)
                .build();
    }
}
