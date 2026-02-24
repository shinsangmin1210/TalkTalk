# TalkTalk Architecture

## 목차
1. [전체 구조](#1-전체-구조)
2. [인증 흐름](#2-인증-흐름)
3. [채팅방 흐름](#3-채팅방-흐름)
4. [실시간 채팅 흐름](#4-실시간-채팅-흐름)
5. [API 목록](#5-api-목록)
6. [WebSocket 명세](#6-websocket-명세)
7. [에러 코드](#7-에러-코드)
8. [패키지 구조](#8-패키지-구조)

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
                              Repository (JPA)
                                       │
                                   MariaDB
                                       │
                              Redis (Refresh Token)
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
            └── accessor.setUser(authentication) → 이후 핸들러에서 Principal로 접근
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
            │       └── Message 저장 (DB)
            └── SimpMessagingTemplate → /sub/room/{roomId} 브로드캐스트
```

### 4-2. 채팅방 구독 (입장)

```
Client → STOMP SUBSCRIBE /sub/room/{roomId}
    └── StompEventListener.handleSubscribe()
            ├── userId로 닉네임 조회
            ├── ChatMessageService.saveSystemMessage()
            │       └── "{닉네임}님이 입장했습니다." SYSTEM 메시지 저장
            └── /sub/room/{roomId} 브로드캐스트
```

### 4-3. 연결 해제 (퇴장)

```
Client → STOMP DISCONNECT
    └── StompEventListener.handleDisconnect()
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

## 5. API 목록

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
| GET | /api/rooms | 내 채팅방 목록 조회 |
| GET | /api/rooms/{roomId} | 채팅방 단건 조회 |
| POST | /api/rooms/{roomId}/members | 멤버 초대 |
| DELETE | /api/rooms/{roomId}/members/me | 채팅방 나가기 |
| GET | /api/rooms/{roomId}/messages | 이전 메시지 조회 (커서 기반) |

---

## 6. WebSocket 명세

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

## 7. 에러 코드

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

## 8. 패키지 구조

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
    ├── config           SecurityConfig, WebSocketConfig
    ├── jwt              JwtProvider, JwtAuthenticationFilter, JwtTokens
    ├── websocket        StompAuthChannelInterceptor, StompEventListener
    ├── exception        BusinessException, ErrorCode, GlobalExceptionHandler
    └── dto              ErrorResponse
```
