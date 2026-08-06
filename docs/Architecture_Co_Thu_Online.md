# SYSTEM ARCHITECTURE DOCUMENT
## Dự án: CỜ THÚ ONLINE (Jungle Chess / Dou Shou Qi)

**Phiên bản:** 1.0
**Ngày:** 05/08/2026
**Liên quan:** SRS_Co_Thu_Online.md (v1.0)

---

## 1. MỤC TIÊU TÀI LIỆU

Tài liệu này mô tả kiến trúc kỹ thuật chi tiết của hệ thống: các thành phần, luồng dữ liệu, giao thức giao tiếp (WebSocket/STOMP), cấu trúc thư mục, thiết kế message, mô hình dữ liệu, và các quyết định kiến trúc (architecture decisions) làm cơ sở để backend và frontend triển khai đồng bộ.

---

## 2. TỔNG QUAN KIẾN TRÚC

```
                         ┌───────────────────────────────────────────┐
                         │              CLIENT (Angular)              │
                         │                                             │
                         │  ┌───────────┐  ┌───────────┐  ┌─────────┐ │
                         │  │ Lobby/Room│  │ Game Board│  │  Bot/    │ │
                         │  │   UI      │  │  UI + Anim│  │  EvE UI  │ │
                         │  └─────┬─────┘  └─────┬─────┘  └────┬────┘ │
                         │        └──────────┬───┴─────────────┘      │
                         │              Game State Service (RxJS)     │
                         │                    │                        │
                         │        ┌───────────┴───────────┐            │
                         │        │  Auth Service (Token)  │            │
                         │        │  WebSocket Service      │           │
                         │        │  (STOMP Client)          │          │
                         │        └───────────┬───────────┘            │
                         └────────────────────┼────────────────────────┘
                                               │ WSS (STOMP over WebSocket)
                                               │ HTTPS (REST: login, refresh)
                         ┌────────────────────┼────────────────────────┐
                         │                SERVER (Spring Boot)         │
                         │                    │                        │
                         │   ┌────────────────┴─────────────────┐      │
                         │   │     WebSocket Gateway (STOMP)     │      │
                         │   │  - Handshake Interceptor (JWT)    │      │
                         │   │  - Channel Interceptor (Auth)     │      │
                         │   └───────────────┬───────────────────┘      │
                         │                    │                         │
                         │   ┌────────────────┼────────────────────┐    │
                         │   │        Application Layer            │    │
                         │   │  ┌──────────┐ ┌──────────────────┐  │    │
                         │   │  │Auth Svc  │ │ Room/Session Mgr │  │    │
                         │   │  └──────────┘ └────────┬─────────┘  │    │
                         │   │  ┌──────────────────┐  │            │    │
                         │   │  │ Game Rule Engine │<─┘            │    │
                         │   │  └────────┬─────────┘               │    │
                         │   │           │                          │    │
                         │   │  ┌────────┴─────────┐                │    │
                         │   │  │   Bot AI Engine   │                │   │
                         │   │  └───────────────────┘                │   │
                         │   └──────────────────────────────────────┘   │
                         │                    │                         │
                         │   ┌────────────────┴─────────────────┐       │
                         │   │   Persistence Layer (JPA/Redis)   │       │
                         │   │  - User, RefreshToken (DB)        │       │
                         │   │  - Match History (DB)             │       │
                         │   │  - Active Room State (in-memory/  │       │
                         │   │    Redis cho scale-out)           │       │
                         │   └────────────────────────────────────┘      │
                         └──────────────────────────────────────────────┘
```

---

## 3. CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ đề xuất |
|---|---|
| Frontend framework | Angular (chuẩn mới nhất tại thời điểm dự án) |
| Realtime client | `@stomp/stompjs` + `sockjs-client` (fallback) |
| State management | RxJS Services (BehaviorSubject) hoặc NgRx nếu độ phức tạp tăng |
| UI rendering bàn cờ | SVG hoặc Canvas (khuyến nghị SVG để dễ animation bằng CSS/GSAP, dễ style theo Angular) |
| Animation | CSS transitions/Angular Animations, hoặc GSAP nếu cần hiệu ứng phức tạp |
| Backend framework | Spring Boot (Web, WebSocket, Security, Data JPA) |
| Realtime server | Spring WebSocket + STOMP broker (SimpleBroker cho MVP, có thể nâng cấp RabbitMQ/ActiveMQ relay khi scale) |
| Auth | Spring Security + JWT (access token) + Refresh token lưu DB/Redis |
| Database | PostgreSQL hoặc MySQL |
| Cache/Session realtime | Redis (tuỳ chọn, cần khi scale nhiều instance backend) |
| Build/Deploy | Docker, Docker Compose (MVP); CI/CD tuỳ hạ tầng nhóm chọn |

---

## 4. KIẾN TRÚC BACKEND (SPRING BOOT)

### 4.1 Các layer

```
com.cothu.backend
├── config/                # WebSocket config, Security config, CORS
├── auth/
│   ├── controller/        # REST: /api/auth/login, /api/auth/refresh
│   ├── service/           # AuthService: sinh/verify JWT, refresh token
│   ├── interceptor/       # WebSocket Handshake & Channel Interceptor (kiểm tra token)
│   └── model/              # User, RefreshToken entity
├── room/
│   ├── controller/        # STOMP @MessageMapping: /app/room/**
│   ├── service/           # RoomService, SessionManager
│   └── model/              # Room, Player, GameSession (in-memory hoặc Redis)
├── game/
│   ├── rule/               # GameRuleEngine: validate move, xác định thắng/thua
│   ├── model/               # Board, Piece, Position, MoveResult
│   └── bot/                 # BotEngine: Minimax + Alpha-Beta, Evaluator
├── history/
│   ├── service/             # Lưu lịch sử ván đấu (tuỳ chọn mở rộng)
│   └── model/
└── common/
    ├── dto/                  # Message DTO dùng chung WebSocket
    └── exception/            # Xử lý lỗi tập trung
```

### 4.2 Nguyên tắc thiết kế
- **Game Rule Engine** là module thuần logic (không phụ thuộc WebSocket/DB), để:
  - `RoomService` gọi khi validate nước đi người chơi gửi lên.
  - `BotEngine` gọi để sinh và đánh giá các nước đi khả thi.
  - Dễ viết unit test độc lập (test luật cờ không cần khởi động WebSocket).
- **RoomService/SessionManager** giữ trạng thái từng phòng (bàn cờ hiện tại, lượt đi, người chơi, chế độ chơi). Với MVP một instance backend, có thể lưu trong `ConcurrentHashMap`; nếu cần scale nhiều instance, chuyển sang Redis.
- **Server luôn là nguồn xác định duy nhất (single source of truth):** mọi nước đi client gửi lên chỉ là "đề xuất", server validate qua Game Rule Engine rồi mới broadcast trạng thái chính thức.

### 4.3 Luồng xác thực qua WebSocket (Auth)

1. Client đăng nhập qua REST `POST /api/auth/login` → nhận `accessToken` (ngắn hạn, VD 15 phút) + `refreshToken` (dài hạn, VD 7 ngày, lưu DB/Redis, có thể thu hồi).
2. Client mở kết nối WebSocket, gửi `accessToken` trong STOMP CONNECT header (`Authorization: Bearer <token>`).
3. `HandshakeInterceptor`/`ChannelInterceptor` phía server verify JWT trước khi cho phép SUBSCRIBE/SEND.
4. Khi `accessToken` sắp hết hạn, client gọi `POST /api/auth/refresh` bằng `refreshToken` để lấy `accessToken` mới **mà không cần đóng kết nối WebSocket hiện tại** (kết nối WebSocket vẫn dùng token cũ cho tới khi hết hạn thật sự; với action mới, client dùng access token mới nếu cần re-authenticate theo thiết kế cụ thể).
5. Nếu `refreshToken` hết hạn/không hợp lệ → buộc đăng nhập lại, đóng kết nối WebSocket.

### 4.4 Thiết kế kênh WebSocket (STOMP Destination)

| Hướng | Destination | Mục đích |
|---|---|---|
| Client → Server | `/app/room/create` | Tạo phòng mới (kèm mode: PVP_LOCAL / PVE / EVE) |
| Client → Server | `/app/room/{roomId}/join` | Tham gia phòng |
| Client → Server | `/app/room/{roomId}/move` | Gửi nước đi (from, to) |
| Client → Server | `/app/room/{roomId}/leave` | Rời phòng |
| Client → Server | `/app/room/{roomId}/rematch` | Yêu cầu chơi lại |
| Server → Client | `/topic/room/{roomId}/state` | Broadcast trạng thái bàn cờ mới nhất sau mỗi nước đi |
| Server → Client | `/topic/room/{roomId}/result` | Thông báo thắng/thua/hòa |
| Server → Client | `/topic/room/{roomId}/players` | Broadcast cập nhật danh sách người chơi trong phòng |
| Server → Client | `/user/queue/room-created` | Trả kết quả tạo phòng riêng cho người gửi request |
| Server → Client | `/user/queue/errors` | Gửi lỗi riêng cho từng client (VD: nước đi không hợp lệ, hết hạn phiên) |

### 4.5 Kiến trúc Bot AI

- **Thuật toán:** Minimax kết hợp Alpha-Beta Pruning, giới hạn độ sâu (configurable) để đảm bảo thời gian phản hồi.
- **Hàm đánh giá (Evaluation function)** xem xét:
  - Giá trị thứ bậc từng quân còn sống.
  - Khoảng cách của quân tới hang đối phương.
  - Vị trí kiểm soát sông/bẫy.
  - Nguy cơ bị ăn quân ở nước tiếp theo.
- **PvE:** sau khi người chơi gửi nước đi hợp lệ, `RoomService` gọi `BotEngine.nextMove(board)`, áp dụng qua Game Rule Engine, rồi broadcast trạng thái mới.
- **EvE:** `RoomService` chạy vòng lặp gọi `BotEngine` cho cả 2 bên (có delay giữa các nước để frontend kịp render animation), tự dừng khi có kết quả.
- **Mở rộng độ khó:** tham số hoá độ sâu tìm kiếm và trọng số hàm đánh giá.

---

## 5. KIẾN TRÚC FRONTEND (ANGULAR)

### 5.1 Cấu trúc thư mục đề xuất

```
src/app
├── core/
│   ├── auth/                # AuthService, TokenStorage, AuthInterceptor (refresh tự động)
│   ├── websocket/            # WebSocketService (STOMP client wrapper), reconnect logic
│   └── guards/                # AuthGuard, RoomGuard
├── features/
│   ├── lobby/                 # Màn hình tạo/chọn phòng, chọn chế độ chơi
│   ├── game-board/             # Component bàn cờ, quân cờ, highlight nước đi
│   ├── game-animation/         # Animation di chuyển/ăn quân, hiệu ứng thắng
│   ├── game-sound/              # SoundService: quản lý phát âm thanh
│   └── game-flow/                # Điều phối trạng thái: lobby → room → playing → ended
├── shared/
│   ├── models/                   # Board, Piece, Move, RoomState (dùng chung UI)
│   ├── rules/                     # (đối chiếu UI) Rule helper: tính ô hợp lệ để highlight
│   └── ui-components/              # Button, Modal, Toast dùng chung
└── assets/
    ├── images/pieces/                # Asset hình các con thú
    ├── images/board/                  # Asset bàn cờ, sông, bẫy, hang
    └── sounds/                          # File âm thanh
```

### 5.2 Nguyên tắc thiết kế Frontend
- **WebSocketService** là điểm duy nhất giao tiếp STOMP: cung cấp API `connect()`, `subscribeRoom(roomId)`, `sendMove(move)`, tự động `reconnect` khi mất kết nối, phát sự kiện trạng thái kết nối cho UI hiển thị.
- **Game State Service** (RxJS `BehaviorSubject`) giữ trạng thái bàn cờ hiện tại nhận từ server, các component chỉ subscribe để render — **không tự tính toán thắng/thua ở client**, chỉ tính toán tạm thời để highlight nước đi hợp lệ (UX), quyết định cuối cùng luôn chờ server xác nhận.
- **Game Flow Service** điều phối chuyển màn hình theo trạng thái phòng (WAITING → PLAYING → ENDED) và theo chế độ chơi (PVP_LOCAL / PVE / EVE) để hiển thị đúng luồng tương tác (VD: PvP cùng máy cho phép cả 2 bên thao tác luân phiên trên cùng UI; EvE chỉ hiển thị, không cho tương tác).
- **Animation & Sound** tách thành service riêng, lắng nghe sự kiện từ Game State Service (move, capture, win) để trigger hiệu ứng, không xen logic nghiệp vụ vào trong.

---

## 6. MÔ HÌNH DỮ LIỆU (ĐỀ XUẤT)

### 6.1 Cơ sở dữ liệu (bền vững)

**User**
| Field | Type |
|---|---|
| id | UUID |
| username | String |
| passwordHash | String (nullable nếu isGuest = true) |
| displayName | String (nullable) |
| isGuest | Boolean |
| createdAt | Timestamp |
| updatedAt | Timestamp |

**RefreshToken**
| Field | Type |
|---|---|
| id | UUID/Long |
| userId | FK → User |
| token | String (hashed) |
| expiresAt | Timestamp |
| revoked | Boolean |

**MatchHistory** *(mở rộng)*
| Field | Type |
|---|---|
| id | UUID/Long |
| roomId | String |
| mode | Enum(PVP_LOCAL, PVE, EVE) |
| result | String |
| moves | JSON (danh sách nước đi) |
| playedAt | Timestamp |

### 6.2 Trạng thái phòng (in-memory / Redis – không cần bền vững tuyệt đối)

**RoomState**
```json
{
  "roomId": "string",
  "mode": "PVP_LOCAL | PVE | EVE",
  "status": "WAITING | PLAYING | ENDED",
  "board": [[ "piece_code|null", ... ]],
  "currentTurn": "PLAYER_1 | PLAYER_2",
  "players": [
    { "sessionId": "string", "side": "PLAYER_1", "isBot": false }
  ],
  "history": [ { "from": [x,y], "to": [x,y], "captured": "piece_code|null" } ]
}
```

---

## 7. LUỒNG XỬ LÝ TIÊU BIỂU (SEQUENCE)

### 7.1 Luồng chơi PvE (người chơi vs bot)
1. Client tạo phòng mode `PVE` → server tạo `RoomState`, gán bot vào side còn lại.
2. Client gửi nước đi qua `/app/room/{id}/move`.
3. Server: `RoomService` → `GameRuleEngine.validate(move)` → nếu hợp lệ, cập nhật board, kiểm tra thắng/thua.
4. Nếu chưa kết thúc và tới lượt bot: `RoomService` gọi `BotEngine.nextMove(board)` → validate lại qua `GameRuleEngine` → cập nhật board.
5. Server broadcast `RoomState` mới qua `/topic/room/{id}/state` (có thể broadcast 2 lần: sau nước người chơi và sau nước bot, hoặc gộp lại tuỳ UX mong muốn cho animation).
6. Nếu có điều kiện thắng/thua → gửi thêm `/topic/room/{id}/result`.

### 7.2 Luồng chơi EvE (bot vs bot)
1. Client tạo phòng mode `EVE` → server gán bot cho cả 2 side.
2. Server tự chạy vòng lặp: `BotEngine` tính nước đi bên hiện tại → validate → cập nhật board → broadcast → delay (VD 1-2s) → lượt bên kia.
3. Client chỉ subscribe `/topic/room/{id}/state` để hiển thị, không có quyền gửi `move`.
4. Kết thúc khi có kết quả, server gửi `/topic/room/{id}/result`.

### 7.3 Luồng PvP cùng máy (1 thiết bị)
1. Client tạo phòng mode `PVP_LOCAL` → server tạo `RoomState` với 2 side đều gán cho cùng 1 `sessionId` (chỉ 1 kết nối WebSocket).
2. Mỗi lượt, UI xác định bên nào đang được phép thao tác dựa vào `currentTurn` nhận từ server (không tự suy luận).
3. Nước đi vẫn gửi qua `/app/room/{id}/move`, server vẫn validate như bình thường, đảm bảo tính nhất quán luật dù chơi cùng máy.

---

## 8. BẢO MẬT

- Toàn bộ giao tiếp qua HTTPS/WSS (không dùng HTTP/WS thuần ở môi trường production).
- Access token JWT ký bằng secret/private key phía server, thời hạn ngắn.
- Refresh token lưu ở DB (hoặc Redis) kèm trạng thái `revoked` để có thể thu hồi khi logout hoặc phát hiện bất thường.
- Channel Interceptor kiểm tra token ở mọi `SEND`/`SUBSCRIBE`, không chỉ ở bước handshake ban đầu, để chặn trường hợp token hết hạn giữa phiên.
- Validate mọi input từ client ở server (không tin tưởng dữ liệu nước đi từ client), tránh cheat qua việc gửi thẳng message giả vào STOMP endpoint.

---

## 9. KHẢ NĂNG MỞ RỘNG (SCALABILITY – ĐỊNH HƯỚNG TƯƠNG LAI)

- MVP: 1 instance Spring Boot, `RoomState` lưu in-memory, `SimpleBroker` cho STOMP.
- Khi cần nhiều instance (scale ngang): chuyển `RoomState` sang Redis (hoặc cơ chế sticky session ở load balancer), dùng STOMP relay qua RabbitMQ/ActiveMQ để broadcast xuyên instance.
- Có thể tách `BotEngine` thành service riêng nếu tải tính toán AI lớn (đặc biệt khi tăng độ sâu tìm kiếm hoặc nhiều phòng EvE chạy song song).

---

## 10. ĐIỂM CẦN THỐNG NHẤT GIỮA CÁC THÀNH VIÊN

| Nội dung | Người liên quan | Ghi chú |
|---|---|---|
| Format toạ độ bàn cờ (VD: `[row, col]` 0-index) | Thắng, Khôi, Lộc | Phải giống nhau tuyệt đối giữa Rule Engine (BE) và Rule helper (FE) |
| DTO message WebSocket (move, state, result, error) | Thắng, Nguyên | Thống nhất field name, kiểu dữ liệu, để tránh parse sai |
| Cơ chế refresh token không ngắt WebSocket | Nghĩa, Nguyên | Cần thống nhất client xử lý ra sao khi access token hết hạn giữa ván |
| Tốc độ delay giữa các nước đi ở chế độ EvE | Thắng, Sơn, Mạnh | Ảnh hưởng trực tiếp tới animation timing phía FE |
| Danh sách mã lỗi (error code) | Thắng, Nghĩa, Nguyên | Dùng chung để FE hiển thị thông báo phù hợp |

---

*Tài liệu kiến trúc v1.0 — cần rà soát cùng team trước khi code, đặc biệt là mục 4.4 (thiết kế message WebSocket) và mục 6 (mô hình dữ liệu) vì đây là hợp đồng (contract) giữa Backend và Frontend.*
