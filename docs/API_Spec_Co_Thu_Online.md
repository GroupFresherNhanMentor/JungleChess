# API SPECIFICATION
## Dự án: CỜ THÚ ONLINE (Jungle Chess / Dou Shou Qi)

**Phiên bản:** 1.0
**Ngày:** 05/08/2026
**Liên quan:** SRS_Co_Thu_Online.md (v1.0), Architecture_Co_Thu_Online.md (v1.0)

> Tài liệu này là **hợp đồng (contract)** giữa Backend và Frontend, gồm 2 phần: REST API (auth, các thao tác không realtime) và RSocket API (toàn bộ luồng chơi game realtime). Mọi thay đổi field/kiểu dữ liệu, route hoặc interaction model phải cập nhật đồng bộ tài liệu này trước khi code.

---

## 1. QUY ƯỚC CHUNG

### 1.1 Base URL / Endpoint
| Loại | Giá trị mẫu |
|---|---|
| REST base URL | `https://api.cothu.online/api` |
| RSocket over WebSocket | `wss://api.cothu.online/rsocket` |

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
  "data": {
    "userId": "u-0001",
    "username": "nghia123"
  },
  "message": "Registration successful",
  "isSuccess": true
}
```

> Tài khoản tự đăng ký được tạo với role `USER` và trạng thái `ACTIVE`. Password được lưu dưới dạng hash; endpoint không trả access token hoặc password.

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

**Response `200 OK`**
```json
{
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "user": {
      "id": "u-0001",
      "username": "guest_a1b2c3d4e5"
    }
  },
  "message": "Guest login successful",
  "isSuccess": true
}
```

> Guest account được lưu với `is_guest=true` và bị dọn sau 7 ngày kể từ `last_activity_at` nếu không có hoạt động hợp lệ. Lịch sử trận đấu vẫn giữ username snapshot của guest.

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

## 3. RSOCKET API

### 3.1 Kết nối & xác thực

- Client kết nối RSocket over WebSocket tới endpoint `/rsocket`.
- Gửi `accessToken` trong authentication metadata của RSocket SETUP/payload. Metadata dùng Bearer JWT:

```
Authorization: Bearer eyJhbGciOi...
```

- Server xác thực JWT metadata trước khi xử lý route và tạo `Principal` gồm `userId`, username và roles.
- Command dùng interaction model `request-response`; event phòng dùng `request-stream`.
- Nếu token không hợp lệ/hết hạn → server từ chối payload/stream. Client cần refresh access token qua REST rồi reconnect RSocket bằng token mới.

### 3.2 Danh sách route và interaction model

| # | Hướng | Route | Interaction | Mô tả |
|---|---|---|---|---|
| 1 | C → S | `room.create` | request-response | Tạo phòng mới |
| 2 | C → S | `room.join` | request-response | Tham gia phòng đã tồn tại |
| 3 | C → S | `room.move` | request-response | Gửi nước đi và nhận kết quả xử lý |
| 4 | C → S | `room.leave` | request-response | Rời phòng |
| 5 | C → S | `room.rematch` | request-response | Yêu cầu chơi lại |
| 6 | S → C | `room.events` | request-stream | Stream state, result, players và error event của phòng |

> Quy ước: client phải mở request-stream `room.events` **ngay sau khi** nhận được `roomId` (từ response `room.create` hoặc `room.join`), trước khi bắt đầu gửi `room.move`.

---

### 3.3 Chi tiết từng message

#### 3.3.1 Tạo phòng — `room.create` (request-response)

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

**Phản hồi** — server trả trực tiếp cho request `room.create`:
```json
{
  "roomId": "room-8f21",
  "mode": "PVE",
  "status": "WAITING",
  "yourSide": "PLAYER_1"
}
```

---

#### 3.3.2 Tham gia phòng — `room.join` (request-response)

**Payload gửi lên**
```json
{}
```
*(roomId đã nằm trong destination; body có thể để trống hoặc mở rộng sau, VD mật khẩu phòng)*

**Phản hồi** — server trả trực tiếp cho request `room.join`; các client trong phòng nhận event `PLAYERS_UPDATED` qua `room.events`:
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

#### 3.3.3 Gửi nước đi — `room.move` (request-response)

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

**Phản hồi** — server trả kết quả xử lý cho request; các client trong phòng nhận event `STATE_UPDATED` qua `room.events`:
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

**Lỗi** — server trả lỗi cho request `room.move`; client trong phòng chỉ nhận các error event liên quan qua `room.events`:
```json
{
  "errorCode": "INVALID_MOVE",
  "message": "Nước đi không hợp lệ theo luật cờ thú",
  "context": { "from": [3, 2], "to": [5, 2] }
}
```

---

#### 3.3.4 Kết quả ván đấu — event `GAME_RESULT` trong `room.events`

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

#### 3.3.5 Rời phòng — `room.leave` (request-response)

**Payload gửi lên:** `{}`

**Event:** cập nhật `PLAYERS_UPDATED` trong `room.events` (loại bỏ người chơi đó), nếu phòng còn 1 người và mode cần 2 người → `status` chuyển về `WAITING` hoặc `ENDED` tuỳ luật xử lý nhóm chọn.

---

#### 3.3.6 Yêu cầu chơi lại — `room.rematch` (request-response)

**Payload gửi lên:** `{}`

**Event:** `STATE_UPDATED` trong `room.events` (trạng thái reset):
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

| errorCode | Ngữ cảnh | HTTP/RSocket |
|---|---|---|
| VALIDATION_ERROR | Dữ liệu request không hợp lệ | REST 400 |
| USERNAME_ALREADY_EXISTS | Đăng ký trùng username | REST 409 |
| INVALID_CREDENTIALS | Sai tài khoản/mật khẩu | REST 401 |
| REFRESH_TOKEN_INVALID | Refresh token sai/không tồn tại | REST 401 |
| REFRESH_TOKEN_EXPIRED | Refresh token hết hạn | REST 401 |
| UNAUTHORIZED_RSOCKET | Access token không hợp lệ khi kết nối hoặc gửi payload RSocket | RSocket rejected payload/stream |
| ROOM_NOT_FOUND | roomId không tồn tại | RSocket error response |
| ROOM_FULL | Phòng đã đủ người chơi | RSocket error response |
| NOT_YOUR_TURN | Gửi move không đúng lượt | RSocket error response |
| INVALID_MOVE | Nước đi phạm luật cờ thú | RSocket error response |
| ACTION_NOT_ALLOWED | Thực hiện hành động không được phép (VD: client gửi move ở chế độ EvE/spectator) | RSocket error response |
| GAME_ALREADY_ENDED | Gửi move sau khi ván đã kết thúc | RSocket error response |

---

## 4. GHI CHÚ TÍCH HỢP CHO TỪNG THÀNH VIÊN

| Thành viên | Phần liên quan trực tiếp |
|---|---|
| Nghĩa (Auth BE) | Mục 2 (REST Auth), mục 3.1 (xác thực RSocket) |
| Thắng (Logic RSocket BE) | Toàn bộ mục 3.2 – 3.4 |
| Khôi (Bot BE) | Payload `board`/`move` ở mục 3.3.3 dùng chung định dạng, `botDifficulty` ở mục 3.3.1 |
| Nguyên (RSocket FE) | Toàn bộ mục 3 — implement RSocket client theo đúng route, interaction model và payload |
| Lộc (Game rule FE) | Định dạng `board`, `pieceCode`, toạ độ `[row, col]` ở mục 1.2 và 3.3.3 |
| Sơn (Animation/Sound FE) | Field `lastMove.specialEvent`, `lastMove.capturedPiece` ở mục 3.3.3 để chọn đúng hiệu ứng |
| Mạnh (Game flow FE) | Mục 3.3.4 (result), trạng thái `status`/`roomStatus` để điều phối chuyển màn hình |
| Tín (UI/Asset) | Bảng `pieceCode` ở mục 1.2 để đặt tên file asset tương ứng (khuyến nghị đặt tên asset trùng `pieceCode`, VD `P1_ELEPHANT.png`) |

---

*Tài liệu API Spec v1.0 — mọi thay đổi field/destination cần được cập nhật vào file này và thông báo cho cả team trước khi merge code liên quan.*
