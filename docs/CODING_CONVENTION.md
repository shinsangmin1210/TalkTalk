# 📘 CODING_CONVENTION.md

# 1. 목적

본 문서는 AI 어시스턴트(ChatGPT, Copilot 등)와의 협업을 전제로 한 코딩 컨벤션을 정의한다.

목표:
- 코드 가독성 향상
- AI 코드 생성 정확도 향상
- 구조 일관성 유지
- 리팩터링 용이성 확보

---

# 2. 공통 원칙
## 2.1 네이밍 원칙

- 역할 + 책임 기반 명명
- 축약어 사용 금지
- 의미 없는 이름 금지 (data, temp, process 등)
- 동사 + 목적어 형태 유지

### ❌ Bad
- UserMng
- process()
- handle()

### ✅ Good
- UserService
- sendMessage()
- markMessageAsRead()

---

## 2.2 구조 원칙

- Controller / Service / Repository 명확 분리
- DTO, Command, Response 객체 분리
- 단일 책임 원칙 준수
- 얕고 명확한 계층 구조 유지

---

# 3. Backend (Java / Spring)

## 3.1 패키지 구조

```
com.toy.talktalk/
├── domain/
│   ├── user/
│   │   ├── controller/     # REST API 엔드포인트
│   │   ├── service/        # 비즈니스 로직
│   │   ├── repository/     # DB 접근
│   │   ├── entity/         # JPA 엔티티
│   │   └── dto/            # Request / Response DTO
│   ├── chat/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   └── ...
├── global/
│   ├── config/             # Security, WebSocket, Redis 등 설정
│   ├── exception/          # 공통 예외 처리
│   ├── jwt/                # JWT 생성 / 검증
│   └── util/               # 공통 유틸
└── TalktalkApplication.java
```

---

## 3.2 계층 구조 규칙

| 계층 | 역할 | 네이밍 |
|------|------|--------|
| Controller | 요청 수신, DTO 변환, 응답 반환 | `XxxController` |
| Service | 비즈니스 로직, 트랜잭션 | `XxxService` |
| Repository | DB 접근 (JPA / QueryDSL) | `XxxRepository` |
| Entity | DB 테이블 매핑 | `Xxx` (도메인 명) |
| DTO | 요청/응답 데이터 전송 | `XxxRequest`, `XxxResponse` |

- Controller는 Service만 호출, Repository를 직접 호출하지 않는다
- Service는 여러 Repository를 조합할 수 있다
- Entity를 Controller 레이어로 노출하지 않는다 (DTO로 변환)

---

## 3.3 클래스 / 메서드 규칙

```java
// ✅ Controller 예시
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<SendMessageResponse> sendMessage(
            @RequestBody @Valid SendMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(messageService.sendMessage(request, userDetails));
    }
}
```

```java
// ✅ Service 예시
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;

    @Transactional
    public SendMessageResponse sendMessage(SendMessageRequest request, UserDetails userDetails) {
        // ...
    }
}
```

- `@Transactional(readOnly = true)` 를 클래스 기본값으로, 쓰기 메서드에만 `@Transactional` 오버라이드
- Lombok `@RequiredArgsConstructor` + `final` 필드로 생성자 주입 사용

---

## 3.4 DTO 규칙

```java
// Request
public record SendMessageRequest(
        @NotBlank String content,
        @NotNull Long roomId
) {}

// Response
public record SendMessageResponse(
        Long messageId,
        String content,
        LocalDateTime sentAt
) {}
```

- Java Record 우선 사용 (불변 DTO)
- 입력 검증은 Request DTO에 `@Valid` + Bean Validation 어노테이션으로 처리
- Response에 Entity 필드를 직접 노출하지 않는다

---

## 3.5 예외 처리 규칙

```java
// 커스텀 예외
public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(Long messageId) {
        super("메시지를 찾을 수 없습니다. id=" + messageId);
    }
}

// 글로벌 핸들러
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotFound(MessageNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
    }
}
```

- 비즈니스 예외는 `RuntimeException` 상속 커스텀 예외로 정의
- `@RestControllerAdvice` 하나에서 중앙 처리

---

## 3.6 WebSocket / STOMP 규칙

- 메시지 발행: `/pub/{topic}`
- 메시지 구독: `/sub/{topic}`
- 채팅방 입장/퇴장은 별도 이벤트 메시지로 처리

```java
@MessageMapping("/chat.send")   // 클라이언트 → 서버
@SendTo("/sub/room/{roomId}")   // 서버 → 구독자
public ChatMessageResponse sendChatMessage(ChatMessageRequest request) { ... }
```

---

## 3.7 JWT 규칙

- Access Token: HTTP Authorization 헤더 (`Bearer {token}`)
- Refresh Token: HttpOnly Cookie
- 토큰 검증 로직은 `global/jwt/` 패키지에 집중

---

# 4. Frontend (React / TypeScript)

## 4.1 디렉토리 구조

```
src/
├── api/            # Axios 인스턴스 및 API 호출 함수
├── components/     # 재사용 가능한 UI 컴포넌트
├── features/       # 도메인별 기능 (Redux slice + 관련 컴포넌트)
│   ├── chat/
│   └── user/
├── hooks/          # 커스텀 React 훅
├── pages/          # 라우트 단위 페이지 컴포넌트
├── store/          # Redux store 설정
├── types/          # 공통 TypeScript 타입 정의
└── utils/          # 공통 유틸 함수
```

---

## 4.2 컴포넌트 규칙

```tsx
// ✅ Good - 함수형 컴포넌트 + Props 타입 명시
interface MessageItemProps {
  content: string;
  sentAt: string;
  isOwn: boolean;
}

const MessageItem = ({ content, sentAt, isOwn }: MessageItemProps) => {
  return (
    <div className={isOwn ? 'message--own' : 'message--other'}>
      <p>{content}</p>
      <span>{sentAt}</span>
    </div>
  );
};

export default MessageItem;
```

- 컴포넌트 파일명: PascalCase (`MessageItem.tsx`)
- 훅, 유틸 파일명: camelCase (`useChatSocket.ts`)
- `default export`는 컴포넌트 하나만

---

## 4.3 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 | PascalCase | `ChatRoomList` |
| 훅 | `use` + PascalCase | `useChatSocket` |
| Redux slice | camelCase + `Slice` | `chatSlice` |
| API 함수 | 동사 + 명사 | `fetchMessages`, `sendMessage` |
| 타입 / 인터페이스 | PascalCase | `ChatMessage`, `UserProfile` |
| 상수 | UPPER_SNAKE_CASE | `MAX_MESSAGE_LENGTH` |

---

## 4.4 API 호출 규칙

```typescript
// api/messageApi.ts
import apiClient from './apiClient';
import { SendMessageRequest, SendMessageResponse } from '@/types/message';

export const sendMessage = async (request: SendMessageRequest): Promise<SendMessageResponse> => {
  const { data } = await apiClient.post('/api/messages', request);
  return data;
};
```

- API 호출 함수는 `api/` 디렉토리에 도메인별로 분리
- 컴포넌트 내에서 직접 `axios` 호출 금지
- 에러 처리는 Axios 인터셉터에서 공통 처리

---

## 4.5 Redux 규칙

```typescript
// features/chat/chatSlice.ts
const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {
    messageReceived: (state, action: PayloadAction<ChatMessage>) => {
      state.messages.push(action.payload);
    },
  },
});
```

- 서버 상태(API 결과): `RTK Query` 또는 `createAsyncThunk` 사용
- 클라이언트 상태(UI 상태): `createSlice` reducer 사용
- selector는 컴포넌트 내 인라인 대신 `features/` 안에 별도 정의

---

## 4.6 WebSocket (STOMP) 규칙

- 연결 / 구독 / 해제 로직은 커스텀 훅(`useChatSocket`)으로 캡슐화
- 컴포넌트가 직접 STOMP 클라이언트를 생성하지 않는다

```typescript
// hooks/useChatSocket.ts
const useChatSocket = (roomId: number) => {
  useEffect(() => {
    const client = new Client({ brokerURL: WS_URL });
    client.onConnect = () => {
      client.subscribe(`/sub/room/${roomId}`, (message) => {
        // dispatch to Redux
      });
    };
    client.activate();
    return () => { client.deactivate(); };
  }, [roomId]);
};
```

---

# 5. Git 커밋 규칙

## 5.1 커밋 메시지 형식

```
<type>: <subject>

[optional body]
```

| type | 설명 |
|------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 코드 개선 |
| `style` | 포매팅, 공백 등 (로직 변경 없음) |
| `test` | 테스트 추가 / 수정 |
| `chore` | 빌드, 설정 변경 |

### 예시
```
feat: 채팅방 메시지 전송 API 구현
fix: JWT 만료 시 자동 갱신 오류 수정
refactor: MessageService 트랜잭션 범위 최소화
```

- subject는 50자 이내, 한국어 또는 영어 일관되게 사용
- 명령형 동사로 시작 (구현, 수정, 추가, 제거)
