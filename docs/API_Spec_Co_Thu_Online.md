# API SPECIFICATION
## Dự án: CỜ THÚ ONLINE (Jungle Chess / Dou Shou Qi)

**Phiên bản:** 1.0
**Ngày:** 05/08/2026
**Liên quan:** SRS_Co_Thu_Online.md (v1.0), Architecture_Co_Thu_Online.md (v1.0)

> Tài liệu này là **hợp đồng (contract)** giữa Backend và Frontend, gồm 2 phần: REST API (auth, các thao tác không realtime) và WebSocket/STOMP API (toàn bộ luồng chơi game realtime). Mọi thay đổi field/kiểu dữ liệu phải cập nhật đồng bộ tài liệu này trước khi code.

---

## 1. QUY ƯỚC CHUNG

### 1.1 Base URL / Endpoint
| Loại | Giá trị mẫu |
|---|---|
| REST base URL | `https://api.cothu.online/api` |
| WebSocket endpoint (SockJS) | `https://api.cothu.online/ws` |
| STOMP over WebSocket thuần | `wss://api.cothu.online/ws` |

### 1.2 Định dạng chung
- Toàn bộ payload: `application/json`, encoding UTF-8.
- Toạ độ bàn cờ: mảng `[row, col]`, **0-index**, `row` từ 0 (hàng phía Player 1) đến 8, `col` từ 0 đến 6 (bàn cờ 9 hàng x 7 cột).
- Thời gian: ISO-8601 UTC (VD: `2026-08-05T10:00:00Z`).
- Mã quân cờ (`pieceCode`): `"<SIDE>_<TYPE>"`, ví dụ `P1_ELEPHANT`, `P2_RAT`.

  | TYPE | Ý nghĩa | Hạng |
  |---|---|---|
  | RAT | Chuột | 1 |
  | CAT | Mèo | 2 |
  | DOG | Chó | 3 |
  | WOLF | Sói | 4 |
  | LEOPARD | Báo | 5 |
  | TIGER | Hổ | 6 |
  | LION | Sư Tử | 7 |
  | ELEPHANT | Voi | 8 |

- Chế độ chơi (`mode`): `PVP_ONLINE` | `PVE` | `EVE`.
- Bên chơi (`side`): `PLAYER_1` | `PLAYER_2`.
- Trạng thái phòng (`roomStatus`): `WAITING` | `PLAYING` | `ENDED`.

### 1.3 Chuẩn phản hồi lỗi REST
```json
{
  "timestamp": "2026-08-05T10:00:00Z",
  "status": 400,
  "errorCode": "INVALID_CREDENTIALS",
  "message": "Sai tài khoản hoặc mật khẩu"
}
```

---

## 2. REST API — AUTH

### 2.1 Đăng ký
`POST /api/auth/register`

**Request**
```json
{
  "username": "nghia123",
  "password": "P@ssw0rd"
}
```

**Response `201 Created`**
```json
{
  "userId": "u-0001",
  "username": "nghia123"
}
```

**Lỗi:** `USERNAME_ALREADY_EXISTS` (409), `VALIDATION_ERROR` (400)

---

### 2.2 Đăng nhập
`POST /api/auth/login`

**Request**
```json
{
  "username": "nghia123",
  "password": "P@ssw0rd"
}
```

**Response `200 OK`**
```json
{
  "accessToken": "eyJhbGciOi...",
  "accessTokenExpiresIn": 900,
  "refreshToken": "8f14e45f-...",
  "refreshTokenExpiresIn": 604800,
  "user": {
    "userId": "u-0001",
    "username": "nghia123"
  }
}
```
> `accessTokenExpiresIn` / `refreshTokenExpiresIn` tính bằng **giây**.

**Lỗi:** `INVALID_CREDENTIALS` (401)

---

### 2.3 Đăng nhập khách (Guest — tuỳ chọn)
`POST /api/auth/guest`

**Response `200 OK`** — cấu trúc giống mục 2.2, `username` được server tự sinh (VD `guest_8213`).

---

### 2.4 Làm mới token
`POST /api/auth/refresh`

**Request**
```json
{
  "refreshToken": "8f14e45f-..."
}
```

**Response `200 OK`**
```json
{
  "accessToken": "eyJhbGciOi...",
  "accessTokenExpiresIn": 900,
  "refreshToken": "3af9c0aa-...",
  "refreshTokenExpiresIn": 604800
}
```
> Đề xuất **refresh token rotation**: mỗi lần refresh trả về refresh token mới, thu hồi token cũ.

**Lỗi:** `REFRESH_TOKEN_INVALID` (401), `REFRESH_TOKEN_EXPIRED` (401)

---

### 2.5 Đăng xuất
`POST /api/auth/logout`

**Request**
```json
{
  "refreshToken": "8f14e45f-..."
}
```
**Response `204 No Content`** — server thu hồi (`revoked = true`) refresh token tương ứng.

---

## 3. WEBSOCKET / STOMP API

### 3.1 Kết nối & xác thực

- Client kết nối tới endpoint `/ws` (SockJS) hoặc `wss://.../ws` (thuần WebSocket).
- Gửi kèm `accessToken` trong STOMP CONNECT header:

```
CONNECT
Authorization:Bearer eyJhbGciOi...
accept-version:1.2
heart-beat:10000,10000
```

- Server xác thực trong `HandshakeInterceptor` (kiểm tra tồn tại token) và `ChannelInterceptor` (verify chữ ký + hạn dùng ở mỗi frame `SEND`/`SUBSCRIBE`).
- Nếu token không hợp lệ/hết hạn → server gửi frame `ERROR` và đóng kết nối. Client cần refresh access token qua REST rồi CONNECT lại.

### 3.2 Danh sách kênh (Destination)

| # | Hướng | Destination | Mô tả |
|---|---|---|---|
| 1 | C → S | `/app/room/create` | Tạo phòng mới |
| 2 | C → S | `/app/room/{roomId}/join` | Tham gia phòng đã tồn tại |
| 3 | C → S | `/app/room/{roomId}/move` | Gửi nước đi |
| 4 | C → S | `/app/room/{roomId}/leave` | Rời phòng |
| 5 | C → S | `/app/room/{roomId}/rematch` | Yêu cầu chơi lại |
| 6 | S → C | `/topic/room/{roomId}/state` | Broadcast trạng thái bàn cờ mới nhất |
| 7 | S → C | `/topic/room/{roomId}/result` | Thông báo kết quả ván đấu |
| 8 | S → C | `/topic/room/{roomId}/players` | Cập nhật danh sách người chơi trong phòng |
| 9 | S → C | `/user/queue/room-created` | Trả kết quả tạo phòng riêng cho người gửi request |
| 10 | S → C | `/user/queue/errors` | Gửi lỗi riêng cho từng client |

> Quy ước: client phải `SUBSCRIBE` vào các topic số 6, 7, 8 **ngay sau khi** nhận được `roomId` (từ kênh 9 hoặc từ response `join`), trước khi bắt đầu gửi `move`.

---

### 3.3 Chi tiết từng message

#### 3.3.1 Tạo phòng — `/app/room/create`

**Payload gửi lên**
```json
{
  "mode": "PVE",
  "botDifficulty": "MEDIUM"
}
```
| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| mode | enum | ✔ | `PVP_ONLINE` \| `PVE` \| `EVE` |
| botDifficulty | enum | chỉ khi mode = PVE/EVE | `EASY` \| `MEDIUM` \| `HARD` |

**Phản hồi** — server gửi tới `/user/queue/room-created`:
```json
{
  "roomId": "room-8f21",
  "mode": "PVE",
  "status": "WAITING",
  "yourSide": "PLAYER_1"
}
```

---

#### 3.3.2 Tham gia phòng — `/app/room/{roomId}/join`

**Payload gửi lên**
```json
{}
```
*(roomId đã nằm trong destination; body có thể để trống hoặc mở rộng sau, VD mật khẩu phòng)*

**Phản hồi** — broadcast tới `/topic/room/{roomId}/players`:
```json
{
  "roomId": "room-8f21",
  "players": [
    { "sessionId": "sess-1", "side": "PLAYER_1", "isBot": false, "username": "nghia123" },
    { "sessionId": "sess-2", "side": "PLAYER_2", "isBot": false, "username": "khoi88" }
  ],
  "status": "PLAYING",
  "yourSide": "PLAYER_2"
}
```
> Ghi chú: Field `yourSide` trong broadcast là thông tin side phân định cho client vừa gửi yêu cầu join (hoặc client dựa vào `sessionId` của mình trong mảng `players` để xác định side).

---

#### 3.3.3 Gửi nước đi — `/app/room/{roomId}/move`

**Payload gửi lên**
```json
{
  "from": [3, 2],
  "to": [4, 2]
}
```
| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| from | `[row, col]` | ✔ | Vị trí quân đang chọn |
| to | `[row, col]` | ✔ | Vị trí muốn di chuyển tới |

**Broadcast phản hồi** tới `/topic/room/{roomId}/state`:
```json
{
  "roomId": "room-8f21",
  "board": [
    ["P2_ELEPHANT", null, null, null, null, null, "P2_LION"],
    ["...9 hàng x 7 cột..."]
  ],
  "currentTurn": "PLAYER_2",
  "lastMove": {
    "from": [3, 2],
    "to": [4, 2],
    "movedPiece": "P1_TIGER",
    "capturedPiece": null,
    "specialEvent": null
  },
  "status": "PLAYING",
  "moveNumber": 12
}
```
| Field | Kiểu | Ghi chú |
|---|---|---|
| board | 9x7 array | `null` = ô trống, ngược lại là `pieceCode` |
| currentTurn | enum | Bên được đi tiếp theo |
| lastMove.capturedPiece | string/null | `pieceCode` quân bị ăn, `null` nếu không ăn |
| lastMove.specialEvent | enum/null | `RIVER_JUMP` \| `TRAP_NEUTRALIZED` \| `null` — phục vụ frontend chọn đúng animation/âm thanh |
| moveNumber | int | Số thứ tự nước đi, dùng để client bỏ qua message đến trễ/trùng |

**Lỗi (gửi riêng tới `/user/queue/errors` cho người gửi nước đi sai):**
```json
{
  "errorCode": "INVALID_MOVE",
  "message": "Nước đi không hợp lệ theo luật cờ thú",
  "context": { "from": [3, 2], "to": [5, 2] }
}
```

---

#### 3.3.4 Kết quả ván đấu — `/topic/room/{roomId}/result`

```json
{
  "roomId": "room-8f21",
  "status": "ENDED",
  "winner": "PLAYER_1",
  "reason": "DEN_REACHED",
  "endedAt": "2026-08-05T10:15:32Z"
}
```
| Field | Kiểu | Ghi chú |
|---|---|---|
| winner | enum/null | `PLAYER_1` \| `PLAYER_2` \| `null` (hòa, nếu luật hỗ trợ) |
| reason | enum | `DEN_REACHED` \| `NO_VALID_MOVE` \| `OPPONENT_DISCONNECTED_TIMEOUT` |

---

#### 3.3.5 Rời phòng — `/app/room/{roomId}/leave`

**Payload gửi lên:** `{}`

**Broadcast:** cập nhật lại `/topic/room/{roomId}/players` (loại bỏ người chơi đó), nếu phòng còn 1 người và mode cần 2 người → `status` chuyển về `WAITING` hoặc `ENDED` tuỳ luật xử lý nhóm chọn.

---

#### 3.3.6 Yêu cầu chơi lại — `/app/room/{roomId}/rematch`

**Payload gửi lên:** `{}`

**Broadcast** tới `/topic/room/{roomId}/state` (trạng thái reset):
```json
{
  "roomId": "room-8f21",
  "board": [ "...bàn cờ khởi tạo lại..." ],
  "currentTurn": "PLAYER_1",
  "lastMove": null,
  "status": "PLAYING",
  "moveNumber": 0
}
```
> Quy ước: đối với `PVP_ONLINE` (2 kết nối khác nhau), rematch gửi yêu cầu tới phòng và khi cả 2 bên (hoặc chủ phòng reset) đồng ý ván mới sẽ khởi tạo lại.

---

### 3.4 Bảng mã lỗi (Error Codes) dùng chung

| errorCode | Ngữ cảnh | HTTP/STOMP |
|---|---|---|
| VALIDATION_ERROR | Dữ liệu request không hợp lệ | REST 400 |
| USERNAME_ALREADY_EXISTS | Đăng ký trùng username | REST 409 |
| INVALID_CREDENTIALS | Sai tài khoản/mật khẩu | REST 401 |
| REFRESH_TOKEN_INVALID | Refresh token sai/không tồn tại | REST 401 |
| REFRESH_TOKEN_EXPIRED | Refresh token hết hạn | REST 401 |
| UNAUTHORIZED_WS | Access token không hợp lệ khi kết nối WS | STOMP ERROR frame |
| ROOM_NOT_FOUND | roomId không tồn tại | `/user/queue/errors` |
| ROOM_FULL | Phòng đã đủ người chơi | `/user/queue/errors` |
| NOT_YOUR_TURN | Gửi move không đúng lượt | `/user/queue/errors` |
| INVALID_MOVE | Nước đi phạm luật cờ thú | `/user/queue/errors` |
| ACTION_NOT_ALLOWED | Thực hiện hành động không được phép (VD: client gửi move ở chế độ EvE/spectator) | `/user/queue/errors` |
| GAME_ALREADY_ENDED | Gửi move sau khi ván đã kết thúc | `/user/queue/errors` |

---

## 4. GHI CHÚ TÍCH HỢP CHO TỪNG THÀNH VIÊN

| Thành viên | Phần liên quan trực tiếp |
|---|---|
| Nghĩa (Auth BE) | Mục 2 (REST Auth), mục 3.1 (xác thực WebSocket) |
| Thắng (Logic WebSocket BE) | Toàn bộ mục 3.2 – 3.4 |
| Khôi (Bot BE) | Payload `board`/`move` ở mục 3.3.3 dùng chung định dạng, `botDifficulty` ở mục 3.3.1 |
| Nguyên (WebSocket FE) | Toàn bộ mục 3 — implement STOMP client theo đúng destination & payload |
| Lộc (Game rule FE) | Định dạng `board`, `pieceCode`, toạ độ `[row, col]` ở mục 1.2 và 3.3.3 |
| Sơn (Animation/Sound FE) | Field `lastMove.specialEvent`, `lastMove.capturedPiece` ở mục 3.3.3 để chọn đúng hiệu ứng |
| Mạnh (Game flow FE) | Mục 3.3.4 (result), trạng thái `status`/`roomStatus` để điều phối chuyển màn hình |
| Tín (UI/Asset) | Bảng `pieceCode` ở mục 1.2 để đặt tên file asset tương ứng (khuyến nghị đặt tên asset trùng `pieceCode`, VD `P1_ELEPHANT.png`) |

---

*Tài liệu API Spec v1.0 — mọi thay đổi field/destination cần được cập nhật vào file này và thông báo cho cả team trước khi merge code liên quan.*
