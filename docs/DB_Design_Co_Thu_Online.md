# DATABASE DESIGN DOCUMENT
## Dự án: CỜ THÚ ONLINE (Jungle Chess / Dou Shou Qi)

**Phiên bản:** 1.0
**Ngày:** 05/08/2026
**Liên quan:** SRS_Co_Thu_Online.md, Architecture_Co_Thu_Online.md, API_Spec_Co_Thu_Online.md
**DBMS đề xuất:** PostgreSQL (tương thích tốt với JSONB cho dữ liệu bàn cờ/lịch sử nước đi)

> Lưu ý: Trạng thái phòng đang chơi (**RoomState**) là dữ liệu **tạm thời**, không cần bền vững tuyệt đối — được lưu **in-memory** hoặc **Redis** (xem mục 6), **không** thuộc phạm vi DB quan hệ này. DB quan hệ chỉ lưu dữ liệu cần tồn tại lâu dài: tài khoản, phiên đăng nhập, lịch sử ván đấu.

---

## 1. PHẠM VI DỮ LIỆU BỀN VỮNG (RELATIONAL DB)

| Bảng | Mục đích |
|---|---|
| `users` | Tài khoản người chơi |
| `refresh_tokens` | Quản lý refresh token, hỗ trợ thu hồi (revoke) |
| `bots` | Danh mục cấu hình bot (độ khó, tham số thuật toán) |
| `matches` | Thông tin tổng quan 1 ván đấu đã kết thúc |
| `match_players` | Người chơi/bot tham gia 1 ván (quan hệ nhiều-nhiều giữa match và user/bot) |
| `match_moves` | Lịch sử từng nước đi trong ván (phục vụ replay) |

---

## 2. SƠ ĐỒ QUAN HỆ (ERD)

```
┌───────────────┐        ┌────────────────────┐
│    users      │        │   refresh_tokens    │
│───────────────│1      *│──────────────────────│
│ id (PK)       │────────│ id (PK)              │
│ username      │        │ user_id (FK)         │
│ password_hash │        │ token_hash           │
│ display_name  │        │ expires_at           │
│ is_guest      │        │ revoked              │
│ created_at    │        │ created_at           │
└───────┬───────┘        └────────────────────┘
        │1
        │
        │*
┌───────┴─────────────┐        ┌──────────────┐
│   match_players       │*    1│    matches    │
│──────────────────────│───────│──────────────│
│ id (PK)               │       │ id (PK)       │
│ match_id (FK)         │       │ room_id       │
│ user_id (FK, nullable)│       │ mode          │
│ bot_id (FK, nullable) │──┐    │ status        │
│ side                  │  │    │ winner_side   │
│ is_bot                │  │    │ end_reason    │
└───────────────────────┘  │    │ started_at    │
                           │    │ ended_at      │
        ┌────────────────┐ │    └───────┬───────┘
        │      bots       │ │            │1
        │─────────────────│ │            │
        │ id (PK)          │<─┘            │*
        │ name             │      ┌───────┴────────┐
        │ difficulty       │      │  match_moves    │
        │ search_depth     │      │─────────────────│
        │ description      │      │ id (PK)         │
        └──────────────────┘      │ match_id (FK)   │
                                   │ move_number     │
                                   │ side            │
                                   │ from_pos        │
                                   │ to_pos          │
                                   │ moved_piece     │
                                   │ captured_piece  │
                                   │ special_event   │
                                   │ created_at      │
                                   └─────────────────┘
```

---

## 3. CHI TIẾT TỪNG BẢNG

### 3.1 `users`
| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK, default `gen_random_uuid()` | Định danh người dùng |
| username | VARCHAR(50) | UNIQUE, NOT NULL | Tên đăng nhập |
| password_hash | VARCHAR(255) | NULL (null nếu `is_guest = true`) | Mật khẩu đã hash (bcrypt) |
| display_name | VARCHAR(100) | NULL | Tên hiển thị (tuỳ chọn) |
| is_guest | BOOLEAN | NOT NULL, default `false` | Tài khoản khách (không cần mật khẩu) |
| created_at | TIMESTAMPTZ | NOT NULL, default `now()` | Ngày tạo |
| updated_at | TIMESTAMPTZ | NOT NULL, default `now()` | Ngày cập nhật gần nhất |

**Index:** `UNIQUE (username)`

---

### 3.2 `refresh_tokens`
| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Định danh token |
| user_id | UUID | FK → `users.id`, NOT NULL | Chủ sở hữu token |
| token_hash | VARCHAR(255) | NOT NULL | Refresh token đã hash (không lưu plain text) |
| expires_at | TIMESTAMPTZ | NOT NULL | Thời điểm hết hạn |
| revoked | BOOLEAN | NOT NULL, default `false` | Đánh dấu đã thu hồi (logout/rotation) |
| replaced_by_token_id | UUID | FK → `refresh_tokens.id`, NULL | Phục vụ refresh token rotation, trỏ tới token mới thay thế |
| created_at | TIMESTAMPTZ | NOT NULL, default `now()` | Ngày tạo |

**Index:** `INDEX (user_id)`, `INDEX (token_hash)`, `INDEX (expires_at)` *(phục vụ job dọn token hết hạn)*

---

### 3.3 `bots`
| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Định danh cấu hình bot |
| name | VARCHAR(50) | NOT NULL | Tên hiển thị (VD "Bot Dễ", "Bot Khó") |
| difficulty | VARCHAR(20) | NOT NULL | `EASY` \| `MEDIUM` \| `HARD` |
| search_depth | INT | NOT NULL | Độ sâu Minimax tương ứng |
| description | TEXT | NULL | Mô tả thêm (tham số hàm đánh giá...) |

> Bảng này chủ yếu phục vụ cấu hình/danh mục hiển thị ở FE khi chọn độ khó; logic thực thi thuật toán nằm ở tầng ứng dụng (`BotEngine`), không nằm trong DB.

**Dữ liệu mẫu (seed):**
```sql
INSERT INTO bots (id, name, difficulty, search_depth, description) VALUES
  (gen_random_uuid(), 'Bot Dễ',      'EASY',   2, 'Độ sâu tìm kiếm thấp, phù hợp người mới'),
  (gen_random_uuid(), 'Bot Trung Bình', 'MEDIUM', 4, 'Cân bằng tốc độ và độ khó'),
  (gen_random_uuid(), 'Bot Khó',     'HARD',   6, 'Tìm kiếm sâu, phản hồi chậm hơn');
```

---

### 3.4 `matches`
| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Định danh ván đấu |
| room_id | VARCHAR(50) | NOT NULL | Mã phòng lúc chơi (tham chiếu tới log realtime) |
| mode | VARCHAR(20) | NOT NULL | `PVP_LOCAL` \| `PVE` \| `EVE` |
| status | VARCHAR(20) | NOT NULL | `PLAYING` \| `ENDED` \| `ABORTED` |
| winner_side | VARCHAR(20) | NULL | `PLAYER_1` \| `PLAYER_2` \| `NULL` (hòa/chưa kết thúc) |
| end_reason | VARCHAR(30) | NULL | `DEN_REACHED` \| `NO_VALID_MOVE` \| `OPPONENT_DISCONNECTED_TIMEOUT` |
| started_at | TIMESTAMPTZ | NOT NULL | Thời điểm bắt đầu |
| ended_at | TIMESTAMPTZ | NULL | Thời điểm kết thúc |

**Index:** `INDEX (room_id)`, `INDEX (mode, status)`, `INDEX (started_at)`

---

### 3.5 `match_players`
| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Định danh dòng |
| match_id | UUID | FK → `matches.id`, NOT NULL | Ván đấu liên quan |
| user_id | UUID | FK → `users.id`, NULL | Người chơi (null nếu `is_bot = true`) |
| bot_id | UUID | FK → `bots.id`, NULL | Bot (null nếu là người chơi thật) |
| side | VARCHAR(20) | NOT NULL | `PLAYER_1` \| `PLAYER_2` |
| is_bot | BOOLEAN | NOT NULL, default `false` | Đánh dấu bên này là bot |

**Ràng buộc kiểm tra (CHECK):** `(user_id IS NOT NULL AND bot_id IS NULL) OR (user_id IS NULL AND bot_id IS NOT NULL)` — đảm bảo mỗi dòng chỉ gắn với **một trong hai**: người chơi hoặc bot.

**Index:** `INDEX (match_id)`, `INDEX (user_id)`

---

### 3.6 `match_moves`
| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | BIGSERIAL | PK | Định danh nước đi |
| match_id | UUID | FK → `matches.id`, NOT NULL | Ván đấu liên quan |
| move_number | INT | NOT NULL | Thứ tự nước đi trong ván (bắt đầu từ 1) |
| side | VARCHAR(20) | NOT NULL | Bên thực hiện nước đi |
| from_pos | JSONB | NOT NULL | Vị trí xuất phát, VD `[3,2]` |
| to_pos | JSONB | NOT NULL | Vị trí đích, VD `[4,2]` |
| moved_piece | VARCHAR(20) | NOT NULL | `pieceCode`, VD `P1_TIGER` |
| captured_piece | VARCHAR(20) | NULL | `pieceCode` quân bị ăn (nếu có) |
| special_event | VARCHAR(30) | NULL | `RIVER_JUMP` \| `TRAP_NEUTRALIZED` |
| created_at | TIMESTAMPTZ | NOT NULL, default `now()` | Thời điểm ghi nhận nước đi |

**Index:** `UNIQUE (match_id, move_number)`, `INDEX (match_id)`

> Bảng này ánh xạ trực tiếp 1-1 với field `lastMove` trong message `/topic/room/{roomId}/state` (xem API_Spec_Co_Thu_Online.md mục 3.3.3) — mỗi lần server broadcast state mới kèm `lastMove` khác `null`, tầng persistence có thể async ghi thêm 1 dòng vào `match_moves`.

---

## 4. DDL (POSTGRESQL)

```sql
-- Bật extension sinh UUID
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255),
    display_name    VARCHAR(100),
    is_guest        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash             VARCHAR(255) NOT NULL,
    expires_at             TIMESTAMPTZ NOT NULL,
    revoked                BOOLEAN NOT NULL DEFAULT false,
    replaced_by_token_id   UUID REFERENCES refresh_tokens(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

CREATE TABLE bots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL,
    difficulty      VARCHAR(20) NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    search_depth    INT NOT NULL,
    description     TEXT
);

CREATE TABLE matches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         VARCHAR(50) NOT NULL,
    mode            VARCHAR(20) NOT NULL CHECK (mode IN ('PVP_LOCAL','PVE','EVE')),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PLAYING','ENDED','ABORTED')),
    winner_side     VARCHAR(20) CHECK (winner_side IN ('PLAYER_1','PLAYER_2')),
    end_reason      VARCHAR(30) CHECK (end_reason IN ('DEN_REACHED','NO_VALID_MOVE','OPPONENT_DISCONNECTED_TIMEOUT')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);
CREATE INDEX idx_matches_room_id ON matches(room_id);
CREATE INDEX idx_matches_mode_status ON matches(mode, status);
CREATE INDEX idx_matches_started_at ON matches(started_at);

CREATE TABLE match_players (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id    UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id),
    bot_id      UUID REFERENCES bots(id),
    side        VARCHAR(20) NOT NULL CHECK (side IN ('PLAYER_1','PLAYER_2')),
    is_bot      BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT chk_player_or_bot CHECK (
        (user_id IS NOT NULL AND bot_id IS NULL) OR
        (user_id IS NULL AND bot_id IS NOT NULL)
    )
);
CREATE INDEX idx_match_players_match_id ON match_players(match_id);
CREATE INDEX idx_match_players_user_id ON match_players(user_id);

CREATE TABLE match_moves (
    id              BIGSERIAL PRIMARY KEY,
    match_id        UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    move_number     INT NOT NULL,
    side            VARCHAR(20) NOT NULL CHECK (side IN ('PLAYER_1','PLAYER_2')),
    from_pos        JSONB NOT NULL,
    to_pos          JSONB NOT NULL,
    moved_piece     VARCHAR(20) NOT NULL,
    captured_piece  VARCHAR(20),
    special_event   VARCHAR(30) CHECK (special_event IN ('RIVER_JUMP','TRAP_NEUTRALIZED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (match_id, move_number)
);
CREATE INDEX idx_match_moves_match_id ON match_moves(match_id);
```

---

## 5. QUAN HỆ VỚI CÁC BẢNG KHÁC / DÙNG CHUNG THÔNG TIN VỚI API SPEC

| Field trong DB | Field tương ứng trong WebSocket message | File tham chiếu |
|---|---|---|
| `matches.room_id` | `roomId` | API_Spec mục 3.3.1 |
| `matches.mode` | `mode` | API_Spec mục 3.3.1 |
| `matches.winner_side` / `end_reason` | `result.winner` / `result.reason` | API_Spec mục 3.3.4 |
| `match_moves.from_pos` / `to_pos` | `lastMove.from` / `lastMove.to` | API_Spec mục 3.3.3 |
| `match_moves.moved_piece` / `captured_piece` | `lastMove.movedPiece` / `lastMove.capturedPiece` | API_Spec mục 3.3.3 |
| `match_moves.special_event` | `lastMove.specialEvent` | API_Spec mục 3.3.3 |
| `bots.difficulty` | `botDifficulty` | API_Spec mục 3.3.1 |

---

## 6. DỮ LIỆU TẠM THỜI (KHÔNG NẰM TRONG DB QUAN HỆ)

`RoomState` (bàn cờ đang chơi, lượt hiện tại, danh sách người chơi trong phòng) là dữ liệu sống ngắn hạn, khuyến nghị:

- **MVP (1 instance backend):** lưu trong `ConcurrentHashMap<String roomId, RoomState>` ở tầng ứng dụng.
- **Khi cần scale nhiều instance:** chuyển sang **Redis**, cấu trúc key đề xuất:
  - `room:{roomId}:state` → JSON của `RoomState` (xem Architecture_Co_Thu_Online.md mục 6.2)
  - TTL hợp lý (VD 30-60 phút không hoạt động) để tự dọn phòng bỏ hoang.

> Khi ván đấu **kết thúc** (`status = ENDED`), tầng ứng dụng mới ghi tổng kết vào `matches` + `match_players` (+ `match_moves` nếu cần lưu lịch sử đầy đủ), sau đó có thể xoá `RoomState` khỏi bộ nhớ/Redis.

---

## 7. GHI CHÚ CHO TỪNG THÀNH VIÊN

| Thành viên | Liên quan |
|---|---|
| Nghĩa (Auth BE) | Bảng `users`, `refresh_tokens` (mục 3.1, 3.2) |
| Thắng (Logic WebSocket BE) | Bảng `matches`, `match_players`, `match_moves` — đặc biệt thời điểm ghi dữ liệu (khi kết thúc ván / theo từng nước đi) |
| Khôi (Bot BE) | Bảng `bots` — cấu hình độ khó, `search_depth` map với `botDifficulty` trong API Spec |

---

*Tài liệu DB Design v1.0 — cần rà soát cùng team backend trước khi khởi tạo migration đầu tiên (khuyến nghị dùng Flyway/Liquibase để quản lý version schema).*
