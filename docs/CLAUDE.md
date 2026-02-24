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

---

## Step 4. 실시간 채팅 (WebSocket / STOMP)
- [x] `WebSocketConfig` — STOMP 엔드포인트, 메시지 브로커 설정
- [x] `Message` 엔티티 설계 — id, chatRoom, sender, content, type(TEXT/IMAGE/SYSTEM), sentAt, isRead
- [x] `MessageRepository`
- [x] STOMP 메시지 수신 핸들러 — `@MessageMapping("/chat.send")`
- [x] 메시지 저장 후 구독자에게 브로드캐스트 — `@SendTo("/sub/room/{roomId}")`
- [x] 입장 / 퇴장 시스템 메시지 처리
- [x] 채팅 이전 메시지 조회 API — `GET /api/rooms/{roomId}/messages` (커서 기반 페이지네이션)

---

## Step 5. Redis 연동 (Pub/Sub 및 캐싱)
- [ ] `RedisConfig` — RedisTemplate, Pub/Sub 설정
- [ ] WebSocket 메시지를 Redis Pub/Sub으로 분산 처리 (다중 서버 확장 대비)
- [ ] 온라인 사용자 상태 관리 — Redis Set (`online:users`)
- [ ] 채팅방 읽지 않은 메시지 수 캐싱

---

## Step 6. 읽음 처리
- [ ] `MessageReadStatus` 엔티티 또는 Redis 기반 읽음 상태 관리
- [ ] 메시지 읽음 처리 API — `POST /api/rooms/{roomId}/messages/read`
- [ ] 읽지 않은 메시지 수 조회 — 채팅방 목록 응답에 포함
- [ ] STOMP를 통한 실시간 읽음 상태 동기화

---

## Step 7. Frontend — 기반 설정
- [ ] React + TypeScript + Vite 프로젝트 생성
- [ ] TailwindCSS 설치 및 설정
- [ ] Redux Toolkit + RTK Query 설정
- [ ] Axios 인스턴스 생성 — baseURL, 인터셉터(JWT 자동 첨부, 401 재발급)
- [ ] STOMP 클라이언트 설정 (`@stomp/stompjs`)
- [ ] React Router 라우팅 설정
- [ ] 인증 상태에 따른 라우트 보호 (`PrivateRoute`)

---

## Step 8. Frontend — 인증 UI
- [ ] 로그인 페이지
- [ ] 회원가입 페이지
- [ ] 로그인 상태 Redux slice (`authSlice`)
- [ ] Access Token 자동 갱신 로직

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

---

## Step 10. 확장 (Electron Desktop App)
- [ ] Electron 프로젝트 설정 — React 앱 임베드
- [ ] 트레이 아이콘 + 알림 기능
- [ ] 자동 업데이트 설정
