# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run tests
./gradlew test

# Clean
./gradlew clean
```

## Architecture

Spring Boot 3.5 application (Java 17, Gradle) with WebSocket support for real-time chat.

**Key dependencies**: Spring Web, Spring Security, Spring WebSocket, Lombok

**Package root**: `com.toy.talktalk`

**Source layout**:
- `src/main/java/com/toy/talktalk/` — Application source code
- `src/main/resources/templates/` — HTML templates (Thymeleaf expected)
- `src/main/resources/static/` — Static assets (CSS, JS)
- `src/main/resources/application.yaml` — Application configuration

The application is in early development. Spring Security is included but not yet configured; WebSocket is included for real-time messaging features.

# Project Name

## 1. 프로젝트 개요

### 목표
-  스타일 실시간 메신저 구현
- Web 기반 + Desktop App(Electron) 확장
- 확장 가능한 실시간 채팅 서버 아키텍처 설계

### 🧱 전체 스택

#### Frontend
- React (TypeScript)
- WebSocket (STOMP)
- Redux Toolkit (상태관리)
- Axios (REST API 통신)
- TailwindCSS (UI)
- Electron (Desktop 확장 예정)

#### Backend
- Java 17+
- Spring Boot
- Spring WebSocket
- Redis (Pub/Sub)
- MariaDB
- JWT 인증

---

# 2. 전체 아키텍처


# 3. TO_DO List

## Step 1. 프로젝트 기반 설정
- [x] `build.gradle` 의존성 추가 — MariaDB, Redis, JWT(jjwt), Spring Data JPA, QueryDSL
- [x] `application.yaml` 환경별 설정 — DB(MariaDB), Redis, JWT secret/만료시간
- [x] 도메인 기반 패키지 구조 생성 — `user`, `chat`, `global`
- [x] `GlobalExceptionHandler` 공통 예외 처리 클래스 작성
- [x] `ErrorResponse` 공통 에러 응답 DTO 작성

> **구현 힌트**
> - `application.yaml`을 `local` / `prod` 프로파일로 분리 (`spring.config.activate.on-profile`)
> - `GlobalExceptionHandler`는 `@RestControllerAdvice` 사용
> - `ErrorResponse`는 Java Record로 작성 (`status`, `code`, `message`, `errors` 필드)
> - `BusinessException(ErrorCode)` 커스텀 예외를 기반으로 에러 처리 통일

---

## Step 2. 회원 / 인증 (User & Auth)
- [x] `User` 엔티티 설계 — id, email, password, nickname, profileImage, createdAt
- [x] `UserRepository` (JPA)
- [x] 회원가입 API — `POST /api/auth/signup`
- [x] 로그인 API — `POST /api/auth/login` → Access Token + Refresh Token 발급
- [x] JWT 생성 / 검증 유틸 (`JwtProvider`)
- [x] `JwtAuthenticationFilter` — Security 필터 체인에 등록
- [x] `SecurityConfig` — 경로별 인증 설정
- [x] Refresh Token 재발급 API — `POST /api/auth/refresh`
- [x] 로그아웃 API — `POST /api/auth/logout` (Refresh Token 만료)
- [x] 내 프로필 조회 API — `GET /api/users/me`
- [x] 프로필 수정 API — `PATCH /api/users/me`

> **구현 순서** : JwtProvider → SecurityConfig → JwtAuthenticationFilter → UserService → AuthService → Controller
>
> **구현 힌트**
> - `JwtProvider`: `io.jsonwebtoken:jjwt-api` 사용, `Keys.hmacShaKeyFor(secret.getBytes())` 로 키 생성
> - `JwtAuthenticationFilter`: `OncePerRequestFilter` 상속, `Authorization: Bearer` 헤더 파싱
> - `SecurityConfig`: `SessionCreationPolicy.STATELESS`, `/api/auth/**` permitAll, 나머지 authenticated
> - Controller의 인증된 userId 접근: `@AuthenticationPrincipal Long userId`
> - Refresh Token: `HttpOnly Cookie`로 발급, Redis에 `refresh:{userId}` 키로 저장 (TTL 7일)
> - 비밀번호: `BCryptPasswordEncoder` 사용 (`@Bean`으로 SecurityConfig에 등록)

---

## Step 3. 채팅방 (ChatRoom)
- [x] `ChatRoom` 엔티티 설계 — id, name, type(DIRECT/GROUP), createdAt
- [x] `ChatRoomMember` 엔티티 설계 — chatRoom, user, joinedAt
- [x] `ChatRoomRepository`, `ChatRoomMemberRepository`
- [x] 채팅방 생성 API — `POST /api/rooms` (1:1 / 그룹)
- [x] 내 채팅방 목록 조회 API — `GET /api/rooms`
- [x] 채팅방 단건 조회 API — `GET /api/rooms/{roomId}`
- [x] 채팅방 멤버 초대 API — `POST /api/rooms/{roomId}/members`
- [x] 채팅방 나가기 API — `DELETE /api/rooms/{roomId}/members/me`

> **구현 힌트**
> - `ChatRoomType` enum (`DIRECT`, `GROUP`) 별도 파일로 분리
> - `ChatRoomMember`에 `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "user_id"}))` 적용
> - `ChatRoom → ChatRoomMember`: `@OneToMany(cascade = ALL, orphanRemoval = true)`
> - `ChatRoomMember → ChatRoom/User`: `@ManyToOne(fetch = LAZY)`
> - 목록 조회는 JPQL `JOIN` 쿼리: `SELECT cr FROM ChatRoom cr JOIN cr.members m WHERE m.user.id = :userId`
> - DIRECT 생성 시 inviteeIds 1명 강제, GROUP 생성 시 name 필수 → 서비스 레이어에서 검증

---

## Step 4. 실시간 채팅 (WebSocket / STOMP)
- [x] `WebSocketConfig` — STOMP 엔드포인트, 메시지 브로커 설정
- [x] `Message` 엔티티 설계 — id, chatRoom, sender, content, type(TEXT/IMAGE/SYSTEM), sentAt, isRead
- [x] `MessageRepository`
- [x] STOMP 메시지 수신 핸들러 — `@MessageMapping("/chat.send")`
- [x] 메시지 저장 후 구독자에게 브로드캐스트 — `@SendTo("/sub/room/{roomId}")`
- [x] 입장 / 퇴장 시스템 메시지 처리
- [x] 채팅 이전 메시지 조회 API — `GET /api/rooms/{roomId}/messages` (커서 기반 페이지네이션)

> **구현 순서** : WebSocketConfig → StompAuthChannelInterceptor → Message 엔티티 → ChatMessageHandler → StompEventListener
>
> **구현 힌트**
> - `WebSocketConfig`: `@EnableWebSocketMessageBroker` + `WebSocketMessageBrokerConfigurer` 구현
>   - 엔드포인트: `/ws` (`.withSockJS()`)
>   - 발행: `/pub`, 구독: `/sub`
> - `StompAuthChannelInterceptor`: `ChannelInterceptor` 구현, `CONNECT` 프레임에서 JWT 파싱 후 `accessor.setUser()` 등록
> - `Message.sender`는 nullable — SYSTEM 메시지 팩토리 메서드 `ofSystem()` 활용
> - `ChatMessageHandler`: `@Controller` + `@MessageMapping` 사용, `Principal`로 senderId 추출
> - 입장 시스템 메시지: `@EventListener(SessionSubscribeEvent)` 로 구독 이벤트 감지
> - 커서 페이지네이션: `id < :cursor ORDER BY id DESC` 쿼리, `limit+1` 조회로 `hasNext` 판단

---

## Step 5. Redis 연동 (Pub/Sub 및 캐싱)
- [x] `RedisConfig` — RedisTemplate, Pub/Sub 설정
- [x] WebSocket 메시지를 Redis Pub/Sub으로 분산 처리 (다중 서버 확장 대비)
- [x] 온라인 사용자 상태 관리 — Redis Set (`online:users`)
- [x] 채팅방 읽지 않은 메시지 수 캐싱

> **구현 순서** : RedisConfig → RedisChatPublisher/Subscriber → RedisSubscriptionManager → OnlineStatusService → UnreadCountService
>
> **구현 힌트**
> - `RedisConfig`: `RedisTemplate<String, String>` (문자열용) + `RedisTemplate<String, Object>` (JSON 직렬화) + `RedisMessageListenerContainer` 3개 Bean 등록
> - `RedisChatSubscriber`: `MessageListener` 구현, `ObjectMapper`로 역직렬화 후 `SimpMessagingTemplate`으로 STOMP 브로드캐스트
> - `RedisSubscriptionManager`: `ConcurrentHashMap`으로 이미 구독된 채널 중복 등록 방지
> - `StompAuthChannelInterceptor`를 `@Component`로 변경해 `OnlineStatusService` 주입
> - 온라인 상태: Redis Set `SADD` / `SREM` / `SISMEMBER`
> - Unread 수: Redis Hash `HINCRBY unread:{roomId} {userId} 1` / `HDEL` / `HGET`

---

## Step 6. 읽음 처리
- [x] `MessageReadStatus` 엔티티 또는 Redis 기반 읽음 상태 관리
- [x] 메시지 읽음 처리 API — `POST /api/rooms/{roomId}/messages/read`
- [x] 읽지 않은 메시지 수 조회 — 채팅방 목록 응답에 포함
- [x] STOMP를 통한 실시간 읽음 상태 동기화

> **구현 힌트**
> - Step 5에서 `unread:{roomId}` Hash + `UnreadCountService`로 이미 구현됨 — 읽음 처리 API 호출 시 `resetUnread()` 연동
> - 읽음 처리 API: `POST /api/rooms/{roomId}/messages/read` → `UnreadCountService.resetUnread()` 호출
> - 실시간 동기화: 읽음 처리 후 `/sub/room/{roomId}` 채널로 읽음 이벤트 발행 (type: `READ_ACK`)

---

## Step 7. Frontend — 기반 설정
- [ ] React + TypeScript + Vite 프로젝트 생성
- [ ] TailwindCSS 설치 및 설정
- [ ] Redux Toolkit + RTK Query 설정
- [ ] Axios 인스턴스 생성 — baseURL, 인터셉터(JWT 자동 첨부, 401 재발급)
- [ ] STOMP 클라이언트 설정 (`@stomp/stompjs`)
- [ ] React Router 라우팅 설정
- [ ] 인증 상태에 따른 라우트 보호 (`PrivateRoute`)

> **구현 힌트**
> - `npm create vite@latest` → React + TypeScript 선택
> - Axios 인터셉터: 요청 시 `Authorization: Bearer {accessToken}` 자동 첨부, 401 응답 시 `/api/auth/refresh` 호출 후 재시도
> - Access Token은 메모리(Redux)에 저장, Refresh Token은 Cookie (HttpOnly, 서버에서 관리)
> - STOMP: `@stomp/stompjs` + `sockjs-client`, CONNECT 시 `Authorization` 헤더 전달
> - `PrivateRoute`: Redux `authSlice`의 인증 상태 확인 후 미인증 시 `/login` 리다이렉트

---

## Step 8. Frontend — 인증 UI
- [ ] 로그인 페이지
- [ ] 회원가입 페이지
- [ ] 로그인 상태 Redux slice (`authSlice`)
- [ ] Access Token 자동 갱신 로직

> **구현 힌트**
> - `authSlice`: `{ accessToken, user }` 상태 관리, `login` / `logout` / `setAccessToken` 액션
> - 자동 갱신: 앱 최초 진입 시 `/api/auth/refresh` 호출해 Access Token 재발급 시도 (Refresh Token Cookie 활용)
> - Axios 401 인터셉터에서 자동 재발급 후 원래 요청 재시도 (큐잉 처리로 중복 재발급 방지)

---

## Step 9. Frontend — 채팅 UI
- [ ] 채팅방 목록 페이지
- [ ] 채팅방 생성 모달
- [ ] 채팅방 상세 (메시지 뷰) 페이지
- [ ] 실시간 메시지 수신 훅 (`useChatSocket`)
- [ ] 메시지 입력창 컴포넌트
- [ ] 이전 메시지 무한 스크롤 (커서 기반)
- [ ] 읽지 않은 메시지 뱃지 표시
- [ ] 온라인 상태 표시

> **구현 힌트**
> - `useChatSocket`: STOMP 클라이언트 연결/구독/해제 로직 캡슐화, 메시지 수신 시 Redux 상태 업데이트
> - STOMP 구독: 채팅방 진입 시 `/sub/room/{roomId}` 구독, 퇴장 시 unsubscribe
> - 무한 스크롤: `IntersectionObserver`로 상단 감지, `cursor` 값으로 이전 메시지 추가 로드
> - 읽지 않은 뱃지: `GET /api/rooms` 응답의 `unreadCount` 활용
> - 온라인 상태: `OnlineStatusService.isOnline()` API 또는 STOMP 이벤트로 실시간 반영

---

## Step 10. 확장 (Electron Desktop App)
- [ ] Electron 프로젝트 설정 — React 앱 임베드
- [ ] 트레이 아이콘 + 알림 기능
- [ ] 자동 업데이트 설정

> **구현 힌트**
> - `electron-vite` 또는 `electron-builder`로 React 앱 임베드
> - 트레이 아이콘: `Tray` + `nativeImage` API, 새 메시지 수신 시 뱃지 업데이트
> - 알림: `Notification` API (포커스 없을 때만 표시)
> - 자동 업데이트: `electron-updater` 라이브러리 활용
