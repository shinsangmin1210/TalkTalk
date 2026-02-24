# TalkTalk Architecture

## 목차
1. [전체 구조](#1-전체-구조)
2. [인증 흐름](#2-인증-흐름)
3. [채팅방 흐름](#3-채팅방-흐름)
4. [실시간 채팅 흐름](#4-실시간-채팅-흐름)
5. [Redis 흐름](#5-redis-흐름)
6. [ERD](#6-erd)
7. [API 목록](#7-api-목록)
8. [WebSocket 명세](#8-websocket-명세)
9. [Redis 키 구조](#9-redis-키-구조)
10. [에러 코드](#10-에러-코드)
11. [패키지 구조](#11-패키지-구조)

---

## 1. 전체 구조

```
Client (React + STOMP)
    │
    ├── REST API (HTTP)          → Spring MVC Controller
    │       └── JWT 인증          → JwtAuthenticationFilter
    │
    └── WebSocket (STOMP/SockJS) → ChatMessageHandler
            └── JWT 인증          → StompAuthChannelInterceptor
                                       │
                              Spring Service Layer
                                       │
                         ┌─────────────┴─────────────┐
                    Repository (JPA)            Redis
                         │                       │
                      MariaDB          ┌──────────┼──────────┐
                                  Refresh Token  온라인    Unread
                                               상태 관리   메시지 수
                                                       │
                                               Redis Pub/Sub
                                            (채팅 메시지 분산)
```

---

## 2. 인증 흐름

### 2-1. 회원가입 / 로그인

```
POST /api/auth/signup
    └── UserService.signup()
            └── 이메일 중복 확인 → User 저장 (비밀번호 BCrypt 인코딩)

POST /api/auth/login
    └── AuthService.login()
            ├── 이메일로 User 조회 → 비밀번호 검증
            ├── JwtProvider.generateTokens() → Access Token + Refresh Token 생성
            ├── Redis에 Refresh Token 저장 (key: "refresh:{userId}", TTL: 7일)
            └── 응답: AccessToken (body) + RefreshToken (HttpOnly Cookie)
```

### 2-2. HTTP 요청 인증

```
Request (Authorization: Bearer {accessToken})
    └── JwtAuthenticationFilter
            ├── 토큰 추출 및 검증 (JwtProvider.validateToken)
            ├── userId 추출 → UsernamePasswordAuthenticationToken 생성
            └── SecurityContextHolder에 등록
                    └── Controller: @AuthenticationPrincipal Long userId
```

### 2-3. Access Token 재발급

```
POST /api/auth/refresh (refreshToken Cookie)
    └── AuthService.refresh()
            ├── 토큰 유효성 검증
            ├── Redis에서 저장된 Refresh Token과 비교
            └── 새 Access Token 발급
```

### 2-4. 로그아웃

```
POST /api/auth/logout (refreshToken Cookie)
    └── AuthService.logout()
            ├── Redis에서 Refresh Token 삭제
            └── refreshToken Cookie 만료 처리 (maxAge = 0)
```

### 2-5. WebSocket 인증

```
STOMP CONNECT (Authorization: Bearer {accessToken} 헤더)
    └── StompAuthChannelInterceptor.preSend()
            ├── 토큰 검증 및 userId 추출
            ├── accessor.setUser(authentication) → 이후 핸들러에서 Principal로 접근
            └── OnlineStatusService.markOnline(userId) → Redis Set에 등록
```

---

## 3. 채팅방 흐름

### 3-1. 채팅방 생성

```
POST /api/rooms { type, name, inviteeIds }
    └── ChatRoomService.createChatRoom()
            ├── 유효성 검사
            │       ├── DIRECT: inviteeIds 정확히 1명
            │       └── GROUP: inviteeIds 1명 이상, name 필수
            ├── ChatRoom 저장
            ├── 생성자 → ChatRoomMember 저장
            └── 초대 대상 → ChatRoomMember 저장 (각각)
```

### 3-2. 채팅방 조회 / 목록

```
GET /api/rooms
    └── ChatRoomRepository.findAllByUserId()
            └── JOIN ChatRoomMember WHERE user.id = userId
                    └── 각 채팅방마다 UnreadCountService.getUnreadCount() 포함

GET /api/rooms/{roomId}
    └── 채팅방 존재 확인 → 요청자 멤버 여부 확인 → 반환
```

### 3-3. 멤버 초대 / 나가기

```
POST /api/rooms/{roomId}/members { inviteeId }
    └── 요청자 멤버 확인 → 초대 대상 중복 확인 → ChatRoomMember 저장

DELETE /api/rooms/{roomId}/members/me
    └── 멤버 확인 → ChatRoomMember 삭제
```

---

## 4. 실시간 채팅 흐름

### 4-1. 메시지 전송

```
Client → /pub/chat.send { roomId, content, type }
    └── ChatMessageHandler.sendMessage()
            ├── Principal에서 senderId 추출
            ├── ChatMessageService.saveMessage()
            │       ├── 채팅방 존재 확인
            │       ├── 발신자 멤버 여부 확인
            │       ├── Message 저장 (DB)
            │       └── UnreadCountService.incrementUnread() → 발신자 제외 멤버 unread +1
            ├── RedisSubscriptionManager.subscribeRoom() → Redis 채널 구독 등록
            └── RedisChatPublisher.publish() → Redis 채널에 발행
                    └── RedisChatSubscriber.onMessage()
                            └── SimpMessagingTemplate → /sub/room/{roomId} 브로드캐스트
```

### 4-2. 채팅방 구독 (입장)

```
Client → STOMP SUBSCRIBE /sub/room/{roomId}
    └── StompEventListener.handleSubscribe()
            ├── RedisSubscriptionManager.subscribeRoom() → Redis 채널 구독 등록
            ├── UnreadCountService.resetUnread() → 해당 유저 unread 초기화
            ├── ChatMessageService.saveSystemMessage()
            │       └── "{닉네임}님이 입장했습니다." SYSTEM 메시지 저장
            └── RedisChatPublisher.publish() → Redis 경유 브로드캐스트
```

### 4-3. 연결 해제 (퇴장)

```
Client → STOMP DISCONNECT
    └── StompEventListener.handleDisconnect()
            ├── OnlineStatusService.markOffline(userId) → Redis Set에서 제거
            └── 로그 기록 (userId, 닉네임)
```

### 4-4. 이전 메시지 조회

```
GET /api/rooms/{roomId}/messages?cursor={messageId}&limit=30
    └── ChatMessageService.getMessages()
            ├── 멤버 여부 확인
            ├── cursor 없음 → 최신 메시지부터 limit+1개 조회
            ├── cursor 있음 → id < cursor 조건으로 limit+1개 조회
            └── 반환: { messages[], hasNext, nextCursor }

※ 무한 스크롤 사용 방법
   1. 첫 진입 시 cursor 없이 요청 → 최신 30개 수신
   2. 위로 스크롤 시 마지막 messageId를 cursor로 다음 요청
   3. hasNext = false면 더 이상 이전 메시지 없음
```

---

## 5. Redis 흐름

### 5-1. Pub/Sub 메시지 분산 처리

```
[서버 A]                         [Redis]                        [서버 B]
ChatMessageHandler               chat:room:{id}            RedisChatSubscriber
    │                                 │                            │
    ├── RedisChatPublisher ──────► Publish                         │
    │                                 │                            │
    │                                 └──────────────────► onMessage()
    │                                                              │
    │                                                   SimpMessagingTemplate
    │                                                              │
    │                                                   /sub/room/{roomId}
    │                                                    (서버 B의 클라이언트)
    │
    └── SimpMessagingTemplate
         /sub/room/{roomId}
          (서버 A의 클라이언트)
```

### 5-2. 온라인 상태

```
CONNECT  → OnlineStatusService.markOnline(userId)   → SADD online:users {userId}
DISCONNECT → OnlineStatusService.markOffline(userId) → SREM online:users {userId}
조회     → OnlineStatusService.isOnline(userId)      → SISMEMBER online:users {userId}
```

### 5-3. 읽지 않은 메시지 수

```
메시지 전송 → incrementUnread(roomId, senderId, memberIds)
                → HINCRBY unread:{roomId} {memberId} 1  (발신자 제외)

채팅방 입장 → resetUnread(roomId, userId)
                → HDEL unread:{roomId} {userId}

목록 조회   → getUnreadCount(roomId, userId)
                → HGET unread:{roomId} {userId}
```

---

## 6. ERD

```
┌─────────────────────┐         ┌──────────────────────────┐
│        users        │         │        chat_rooms        │
├─────────────────────┤         ├──────────────────────────┤
│ id          BIGINT  │         │ id          BIGINT       │
│ email       VARCHAR │         │ name        VARCHAR(NULL)│
│ password    VARCHAR │         │ type        VARCHAR      │ ← DIRECT / GROUP
│ nickname    VARCHAR │         │ created_at  DATETIME     │
│ profile_image_url   │         └────────────┬─────────────┘
│             VARCHAR │                      │ 1
│ role        VARCHAR │                      │
│ created_at  DATETIME│                      │ N
│ updated_at  DATETIME│         ┌────────────▼─────────────┐
└────────┬────────────┘         │     chat_room_members    │
         │ 1                    ├──────────────────────────┤
         │                      │ id           BIGINT      │
         │ N                    │ chat_room_id BIGINT (FK) │
         └──────────────────────► user_id      BIGINT (FK) │
                                │ joined_at    DATETIME    │
                                └──────────────────────────┘
                                  UNIQUE(chat_room_id, user_id)

┌─────────────────────────────────────────┐
│                messages                 │
├─────────────────────────────────────────┤
│ id           BIGINT                     │
│ chat_room_id BIGINT (FK → chat_rooms)   │
│ sender_id    BIGINT (FK → users, NULL)  │ ← SYSTEM 메시지는 NULL
│ content      TEXT                       │
│ type         VARCHAR                    │ ← TEXT / IMAGE / SYSTEM
│ is_read      BOOLEAN                    │
│ sent_at      DATETIME                   │
└─────────────────────────────────────────┘
```

**관계 요약**
| 관계 | 설명 |
|------|------|
| User 1:N ChatRoomMember | 한 유저는 여러 채팅방에 참여 가능 |
| ChatRoom 1:N ChatRoomMember | 한 채팅방에 여러 멤버 |
| ChatRoom 1:N Message | 한 채팅방에 여러 메시지 |
| User 1:N Message | 한 유저가 여러 메시지 발신 (sender_id nullable) |

---

## 7. API 목록

### 인증 (공개)

| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/auth/signup | 회원가입 |
| POST | /api/auth/login | 로그인 (Access Token + Refresh Token 발급) |
| POST | /api/auth/refresh | Access Token 재발급 (Refresh Token Cookie 필요) |
| POST | /api/auth/logout | 로그아웃 |

### 사용자 (인증 필요)

| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/users/me | 내 프로필 조회 |
| PATCH | /api/users/me | 내 프로필 수정 |

### 채팅방 (인증 필요)

| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/rooms | 채팅방 생성 |
| GET | /api/rooms | 내 채팅방 목록 조회 (unreadCount 포함) |
| GET | /api/rooms/{roomId} | 채팅방 단건 조회 |
| POST | /api/rooms/{roomId}/members | 멤버 초대 |
| DELETE | /api/rooms/{roomId}/members/me | 채팅방 나가기 |
| GET | /api/rooms/{roomId}/messages | 이전 메시지 조회 (커서 기반) |

---

## 7. WebSocket 명세

### 연결

```
URL: ws://host/ws (SockJS 지원)
CONNECT 헤더: Authorization: Bearer {accessToken}
```

### 발행 (Client → Server)

| Destination | Body | 설명 |
|-------------|------|------|
| /pub/chat.send | `{ roomId, content, type }` | 메시지 전송 |

### 구독 (Client → Server)

| Destination | 설명 |
|-------------|------|
| /sub/room/{roomId} | 채팅방 실시간 메시지 수신 |

### 메시지 타입

| type | 설명 |
|------|------|
| TEXT | 일반 텍스트 메시지 |
| IMAGE | 이미지 메시지 |
| SYSTEM | 입장/퇴장 등 시스템 메시지 (sender = null) |

---

## 8. Redis 키 구조

| Key 패턴 | 자료구조 | 설명 |
|----------|----------|------|
| `refresh:{userId}` | String | Refresh Token (TTL: 7일) |
| `online:users` | Set | 현재 온라인 userId 목록 |
| `unread:{roomId}` | Hash `{ userId: count }` | 채팅방별 읽지 않은 메시지 수 |
| `chat:room:{roomId}` | Pub/Sub Channel | 채팅 메시지 분산 채널 |

---

## 9. 에러 코드

| Code | HTTP | 설명 |
|------|------|------|
| INVALID_INPUT | 400 | 입력값 오류 |
| UNAUTHORIZED | 401 | 인증 필요 |
| FORBIDDEN | 403 | 접근 권한 없음 |
| NOT_FOUND | 404 | 리소스 없음 |
| USER_NOT_FOUND | 404 | 사용자 없음 |
| EMAIL_ALREADY_EXISTS | 409 | 이메일 중복 |
| INVALID_PASSWORD | 401 | 비밀번호 불일치 |
| INVALID_TOKEN | 401 | 유효하지 않은 토큰 |
| EXPIRED_TOKEN | 401 | 만료된 토큰 |
| CHAT_ROOM_NOT_FOUND | 404 | 채팅방 없음 |
| ALREADY_JOINED_ROOM | 409 | 이미 참여 중인 채팅방 |
| NOT_ROOM_MEMBER | 403 | 채팅방 멤버 아님 |

---

## 10. 패키지 구조

```
com.toy.talktalk
├── domain
│   ├── user
│   │   ├── controller   AuthController, UserController
│   │   ├── service      AuthService, UserService
│   │   ├── repository   UserRepository
│   │   ├── entity       User
│   │   └── dto          SignupRequest, LoginRequest, LoginResponse,
│   │                    UserProfileResponse, UpdateProfileRequest
│   └── chat
│       ├── controller   ChatRoomController, ChatMessageHandler
│       ├── service      ChatRoomService, ChatMessageService
│       ├── repository   ChatRoomRepository, ChatRoomMemberRepository, MessageRepository
│       ├── entity       ChatRoom, ChatRoomMember, ChatRoomType,
│       │                Message, MessageType
│       └── dto          CreateChatRoomRequest, ChatRoomResponse,
│                        InviteMemberRequest, ChatMessageRequest,
│                        ChatMessageResponse, MessagePageResponse
└── global
    ├── config           SecurityConfig, WebSocketConfig, RedisConfig
    ├── jwt              JwtProvider, JwtAuthenticationFilter, JwtTokens
    ├── websocket        StompAuthChannelInterceptor, StompEventListener
    ├── redis            RedisChatPublisher, RedisChatSubscriber,
    │                    RedisSubscriptionManager, OnlineStatusService,
    │                    UnreadCountService
    ├── exception        BusinessException, ErrorCode, GlobalExceptionHandler
    └── dto              ErrorResponse
```
