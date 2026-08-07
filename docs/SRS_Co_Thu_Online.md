# SOFTWARE REQUIREMENTS SPECIFICATION (SRS)
## Dự án: CỜ THÚ ONLINE (Jungle Chess / Dou Shou Qi)

**Phiên bản:** 1.0
**Ngày:** 05/08/2026
**Công nghệ:** Angular (Frontend) + Spring Boot (Backend) + RSocket over WebSocket

---

## 1. GIỚI THIỆU

### 1.1 Mục đích
Tài liệu này mô tả chi tiết các yêu cầu chức năng và phi chức năng cho dự án xây dựng game Cờ Thú (Đấu Thú Kỳ / Dou Shou Qi) trực tuyến, tuân theo **luật cờ thú quốc tế**, hỗ trợ 3 chế độ chơi: **PvP Online (trực tuyến giữa 2 người chơi qua WebSocket/STOMP)**, **PvE (đấu với bot)**, **EvE (bot đấu bot)**. Tài liệu dùng làm cơ sở để đội ngũ phát triển (backend, frontend, AI/bot) thống nhất phạm vi, thiết kế và phân chia công việc.

### 1.2 Phạm vi dự án
Sản phẩm là một web-app cho phép:
- Người chơi tạo/tham gia phòng chơi qua kết nối thời gian thực (RSocket over WebSocket).
- Xác thực người dùng bằng access token/refresh token.
- Chơi cờ thú theo đúng luật quốc tế (bàn cờ 9 hàng x 7 cột, bẫy, hang, sông, thứ bậc động vật, luật chuột-voi, luật nhảy sông của hổ/sư tử).
- Ba chế độ chơi: PvP Online (2 người chơi kết nối qua mạng từ 2 thiết bị khác nhau bằng mã phòng/room code), PvE (người chơi đấu với AI bot), EvE (2 bot tự đấu, người dùng xem/quan sát).
- Giao diện đẹp, mượt, có animation và âm thanh.

### 1.3 Định nghĩa, từ viết tắt
| Thuật ngữ | Giải thích |
|---|---|
| PvP | Player vs Player (Online multiplayer) |
| PvE | Player vs Environment (Bot) |
| EvE | Environment vs Environment (Bot vs Bot) |
| SRS | Software Requirements Specification |
| JWT | JSON Web Token |
| RSocket | Giao thức reactive request/response và streaming chạy trên WebSocket |
| Den (Hang) | Ô đích, thắng khi vào được hang đối phương |
| Trap (Bẫy) | Ô làm mất hiệu lực thứ bậc quân cờ đối phương |
| River (Sông) | Vùng chỉ Chuột và Hổ/Sư Tử (khi nhảy) đi qua được |

### 1.4 Tài liệu tham khảo
- Luật chơi Dou Shou Qi (Animal Chess) quốc tế – Jungle Chess.
- Tài liệu Spring RSocket và RSocket JavaScript client.
- Angular Style Guide chính thức.

---

## 2. MÔ TẢ TỔNG QUAN

### 2.1 Bối cảnh sản phẩm
Hệ thống gồm 2 phần chính:
- **Backend (Spring Boot):** xử lý xác thực, quản lý phòng chơi, logic luật chơi, đồng bộ trạng thái ván đấu qua RSocket, engine AI (bot).
- **Frontend (Angular):** giao diện bàn cờ, animation quân cờ, âm thanh, quản lý kết nối RSocket, hiển thị phòng/sảnh chờ, xử lý luồng chơi cho cả 3 chế độ.

### 2.2 Chức năng chính
1. Đăng ký/Đăng nhập qua REST API, xác thực và quản lý phiên (access token + refresh token) cho kết nối RSocket.
2. Tạo phòng, tham gia phòng qua mã phòng (Room ID), rời phòng, yêu cầu chơi lại (rematch).
3. Chơi cờ thú theo luật quốc tế, đồng bộ real-time giữa các client.
4. Bot AI có khả năng tính nước đi (tối thiểu 1 mức độ khó, có thể mở rộng nhiều mức).
5. Chế độ PvE: người chơi vs bot; EvE: bot vs bot (người xem).
6. Chế độ PvP Online: 2 người chơi đăng nhập trên 2 trình duyệt/thiết bị khác nhau, tạo phòng/join phòng qua mã phòng, thi đấu trực tuyến thời gian thực.
7. Giao diện: vẽ bàn cờ, quân cờ, hiệu ứng di chuyển/ăn quân, âm thanh, thông báo thắng/thua/hòa.

### 2.3 Đối tượng người dùng
- **Người chơi ẩn danh/đăng ký:** chơi PvP, PvE.
- **Người xem:** theo dõi ván EvE hoặc theo dõi phòng PvP đang diễn ra (tuỳ chọn mở rộng).
- **Quản trị viên (tuỳ chọn mở rộng):** quản lý tài khoản, theo dõi phòng.

### 2.4 Ràng buộc chung
- Giao tiếp thời gian thực phải dùng RSocket over WebSocket (không dùng polling).
- Giao diện phải responsive, mượt trên desktop (ưu tiên), animation không giật lag.
- Backend đảm bảo tính nhất quán trạng thái ván đấu (single source of truth ở server, client không tự ý cập nhật trạng thái thắng/thua).

---

## 3. LUẬT CHƠI CỜ THÚ QUỐC TẾ (tóm tắt làm cơ sở cho module Game Rule)

- **Bàn cờ:** 9 hàng x 7 cột.
- **Quân cờ (mỗi bên 8 quân, xếp hạng từ thấp đến cao):** Chuột (1) < Mèo (2) < Chó (3) < Sói (4) < Báo (5) < Hổ (6) < Sư Tử (7) < Voi (8).
- **Nguyên tắc ăn quân:** quân có hạng cao hơn hoặc bằng ăn được quân hạng thấp hơn hoặc bằng, **ngoại lệ:** Chuột ăn được Voi (hạng thấp nhất khắc chế hạng cao nhất), nhưng Voi không ăn được Chuột.
- **Sông (River):** chỉ Chuột được đi vào/bơi qua; Hổ và Sư Tử được phép **nhảy thẳng qua sông** theo hàng/cột (bị chặn nếu có Chuột đang ở giữa sông trên đường nhảy).
- **Bẫy (Trap):** đặt trước hang của mỗi bên (3 ô/bên). Quân đối phương đứng vào bẫy của mình sẽ bị mất hiệu lực thứ bậc (hạng = 0), có thể bị bất kỳ quân nào của đối phương ăn.
- **Hang (Den):** ô đích của mỗi bên. Quân của mình không được đi vào hang của chính mình. Thắng khi đưa được bất kỳ quân nào của mình vào hang đối phương.
- **Điều kiện thắng:** (1) một quân vào được hang đối phương, hoặc (2) đối phương không còn quân nào để đi (bị chặn hoàn toàn).
- **Lượt đi:** mỗi lượt di chuyển 1 quân sang ô liền kề (ngang/dọc), trừ luật nhảy sông của Hổ/Sư Tử.

*(Module Game Rule cần đặc tả chi tiết toạ độ bàn cờ, vị trí bẫy/hang/sông chuẩn quốc tế, và các bộ test-case luật để đối chiếu.)*

---

## 4. YÊU CẦU CHỨC NĂNG

### 4.1 Module Xác thực (Auth) – Backend qua RSocket
| Mã | Yêu cầu |
|---|---|
| AUTH-01 | Hệ thống cho phép đăng ký tài khoản (username/password hoặc guest). |
| AUTH-02 | Hệ thống cấp Access Token (thời hạn ngắn) và Refresh Token (thời hạn dài) khi đăng nhập thành công. |
| AUTH-03 | Kết nối RSocket phải xác thực bằng Access Token trong metadata của RSocket SETUP/payload. |
| AUTH-04 | Hệ thống cung cấp cơ chế làm mới Access Token bằng Refresh Token; client refresh qua REST rồi reconnect RSocket bằng token mới khi token hiện tại hết hạn. |
| AUTH-05 | Phân quyền (Authorization): phân biệt người chơi thường và admin (nếu có), giới hạn hành động theo vai trò (VD: chỉ chủ phòng được bắt đầu ván). |
| AUTH-06 | Hệ thống từ chối payload hoặc đóng kết nối RSocket nếu token không hợp lệ hoặc hết hạn mà không refresh được. |
| AUTH-07 | Frontend lưu trữ token an toàn (không lưu access token nhạy cảm ở nơi dễ bị XSS), tự động gọi refresh khi access token gần hết hạn. |

### 4.2 Module Quản lý phòng & Logic RSocket (Room/Game Session)
| Mã | Yêu cầu |
|---|---|
| ROOM-01 | Người chơi có thể tạo phòng mới, chọn chế độ chơi (PvP Online / PvE / EvE). |
| ROOM-02 | Người chơi có thể tham gia (join) phòng đã tồn tại bằng mã phòng (Room ID) cho chế độ PvP Online. |
| ROOM-03 | Hệ thống quản lý danh sách phòng đang hoạt động, trạng thái (đang chờ, đang chơi, đã kết thúc). |
| ROOM-04 | Mỗi nước đi được gửi lên server qua RSocket, server kiểm tra hợp lệ theo luật trước khi phát trạng thái mới tới stream realtime của các client trong phòng. |
| ROOM-05 | Server là nguồn xác định duy nhất (source of truth) cho: lượt đi hiện tại, trạng thái bàn cờ, thắng/thua/hòa. |
| ROOM-06 | Hệ thống phát hiện và thông báo điều kiện kết thúc ván (vào hang, hết nước đi, hoặc người chơi ngắt kết nối/timeout). |
| ROOM-07 | Hệ thống hỗ trợ chơi lại (rematch) hoặc rời phòng sau khi kết thúc ván. |
| ROOM-08 | Xử lý sự cố mất kết nối: cho phép reconnect trong thời gian timeout nhất định mà không hủy ván. |
| ROOM-09 | Ghi log/lịch sử nước đi của ván đấu (phục vụ replay, debug, thống kê – có thể là mở rộng). |

### 4.3 Module Bot / AI
| Mã | Yêu cầu |
|---|---|
| BOT-01 | Bot phải tính toán và trả về nước đi hợp lệ theo đúng luật cờ thú trong thời gian giới hạn (VD: < 2-3 giây/nước ở mức cơ bản). |
| BOT-02 | Bot dùng thuật toán tìm kiếm nước đi (đề xuất: Minimax + Alpha-Beta Pruning, có hàm đánh giá dựa trên thứ bậc quân, vị trí bẫy/hang/sông, khoảng cách tới hang). |
| BOT-03 | Hỗ trợ chế độ PvE: bot đóng vai một bên, phản hồi nước đi ngay sau khi người chơi đi xong. |
| BOT-04 | Hỗ trợ chế độ EvE: hai instance bot tự động đấu với nhau, server điều phối lượt và gửi trạng thái để frontend hiển thị như một trận đấu tự động. |
| BOT-05 | (Mở rộng) Hỗ trợ nhiều mức độ khó (dễ/trung bình/khó) bằng cách điều chỉnh độ sâu tìm kiếm hoặc thêm yếu tố ngẫu nhiên có kiểm soát. |
| BOT-06 | Bot phải tuân thủ tuyệt đối luật chơi (không đi nước bất hợp lệ), tận dụng lại logic kiểm tra luật dùng chung với module Room/Game logic để tránh sai lệch luật giữa 2 module. |

### 4.4 Module Luật chơi (Game Rule Engine – dùng chung Backend/Frontend)
| Mã | Yêu cầu |
|---|---|
| RULE-01 | Định nghĩa bàn cờ 9 hàng x 7 cột, vị trí sông, bẫy, hang theo đúng chuẩn quốc tế. |
| RULE-02 | Kiểm tra tính hợp lệ của nước đi: đúng hướng liền kề, đúng luật ăn quân theo hạng, luật đặc biệt Chuột–Voi, luật bẫy, luật sông. |
| RULE-03 | Xử lý luật nhảy sông của Hổ/Sư Tử (kiểm tra đường nhảy có bị Chuột chặn không). |
| RULE-04 | Xác định điều kiện thắng/thua/hòa sau mỗi nước đi. |
| RULE-05 | Cung cấp API/service dùng chung cho: kiểm tra luật khi người chơi đi (server validate), engine AI (sinh nước đi hợp lệ), và frontend (gợi ý nước đi hợp lệ để highlight ô có thể đi). |

### 4.5 Module Giao diện & Trải nghiệm (Frontend)
| Mã | Yêu cầu |
|---|---|
| UI-01 | Vẽ bàn cờ 9 hàng x 7 cột đúng bố cục quốc tế (sông, bẫy, hang) với asset đồ họa các con thú. |
| UI-02 | Highlight ô có thể đi khi chọn 1 quân cờ. |
| UI-03 | Animation di chuyển quân cờ mượt (drag/drop hoặc click-click), animation khi ăn quân, animation khi vào hang (thắng). |
| UI-04 | Âm thanh: đi quân, ăn quân, thắng/thua, thông báo lượt đối phương. |
| UI-05 | Giao diện sảnh chờ: tạo phòng, nhập mã phòng join phòng, chọn chế độ chơi (PvP Online/PvE/EvE), chọn mức độ bot (nếu có). |
| UI-06 | Giao diện chế độ PvP Online: hiển thị rõ thông tin 2 người chơi trong phòng, hiển thị lượt của bên nào, nút tạo/sao chép mã phòng để gửi cho bạn bè. |
| UI-07 | Giao diện chế độ EvE: chỉ hiển thị, không cho tương tác chọn quân; có nút play/pause/tốc độ hiển thị (tuỳ chọn). |
| UI-08 | Responsive tối thiểu trên desktop, tối ưu trải nghiệm mượt (60fps animation, không giật khi nhận dữ liệu từ RSocket stream). |
| UI-09 | Kết nối RSocket phía frontend: quản lý connection và stream, tự động reconnect, xử lý mất kết nối/hiển thị trạng thái mạng. |
| UI-10 | Xử lý luồng chơi game tổng thể (Game Flow): điều phối giữa các màn hình sảnh chờ → phòng chờ → bàn cờ → kết thúc ván → rematch/thoát.|

---

## 5. YÊU CẦU PHI CHỨC NĂNG

| Mã | Hạng mục | Mô tả |
|---|---|---|
| NFR-01 | Hiệu năng | Độ trễ đồng bộ nước đi qua RSocket < 200ms trong điều kiện mạng bình thường. |
| NFR-02 | Bảo mật | Access token có thời hạn ngắn (VD 15 phút), refresh token thời hạn dài hơn (VD 7 ngày), lưu trữ và truyền tải an toàn (HTTPS/WSS). |
| NFR-03 | Khả năng mở rộng | Kiến trúc backend cho phép mở rộng nhiều phòng chơi đồng thời (session theo phòng độc lập). |
| NFR-04 | Tính nhất quán | Trạng thái ván đấu luôn đồng nhất giữa các client nhờ server là nguồn xác định duy nhất. |
| NFR-05 | Khả năng bảo trì | Logic luật chơi tách thành module dùng chung, dễ test độc lập (unit test luật cờ). |
| NFR-06 | Trải nghiệm người dùng | Giao diện đẹp, animation mượt, phản hồi tức thời khi thao tác. |
| NFR-07 | Độ tin cậy | Xử lý mất kết nối/reconnect không làm hỏng trạng thái ván đấu. |

---

## 6. KIẾN TRÚC HỆ THỐNG (đề xuất)

```
┌─────────────────────────┐        RSocket over WebSocket         ┌──────────────────────────┐
│   Angular Frontend       │ <-------------------------------------> │   Spring Boot Backend    │
│  - Room/Lobby UI         │                                         │  - Auth (JWT + Refresh)  │
│  - Game Board (Canvas/   │                                         │  - RSocket Gateway        │
│    SVG/DOM + animation)  │                                         │  - Room/Session Manager  │
│  - RSocket Client        │                                         │  - Game Rule Engine       │
│  - State Management      │                                         │  - Bot AI Engine          │
│    (NgRx/Service+RxJS)   │                                         │  - Persistence (DB)      │
└─────────────────────────┘                                         └──────────────────────────┘
```

- **Giao thức:** RSocket over WebSocket tại endpoint `/rsocket` cho realtime; REST cho các thao tác không realtime (login, refresh token, lấy lịch sử...).
- **RSocket route đề xuất:** command dùng request-response (`room.create`, `room.join`, `room.move`, `room.leave`, `room.rematch`); event phòng dùng request-stream (`room.events`).
- **Lưu trữ:** DB quan hệ (VD: PostgreSQL/MySQL) cho tài khoản, lịch sử ván đấu; có thể dùng in-memory (Map/Redis) để quản lý trạng thái phòng đang hoạt động cho tốc độ cao.

---

## 7. PHÂN CÔNG CÔNG VIỆC HIỆN TẠI

### Backend
| Thành viên | Công việc |
|---|---|
| Nghĩa | Auth qua RSocket: access token, refresh token, authentication, authorization; phối hợp định nghĩa hợp đồng auth cho Frontend |
| Khôi | Bot chơi game (AI engine: minimax/alpha-beta, tích hợp luật chơi, phục vụ PvE và EvE) |
| Thắng | Logic RSocket: tạo phòng, join phòng, xử lý nước đi, xác định thắng/thua, điều phối luồng ván đấu |

### Frontend
| Thành viên | Công việc |
|---|---|
| Tín | Game asset, vẽ UI (bàn cờ, quân cờ, sảnh chờ, các màn hình) |
| Sơn | Game animation, sound |
| Lộc | Game rule (áp dụng luật cờ thú phía client: highlight nước đi hợp lệ, validate tạm thời trước khi gửi server) |
| Nguyên | RSocket frontend (kết nối, request/stream, reconnect, state sync) |
| Mạnh | Chơi game (luồng chơi/game flow: điều phối UI theo trạng thái ván đấu, xử lý chế độ PvP Online/PvE/EvE ở tầng trải nghiệm) |

> **Lưu ý phối hợp:** Module "Game Rule" nên được thiết kế thống nhất về mặt đặc tả (toạ độ, luật) giữa backend (Thắng/Khôi dùng để validate & AI) và frontend (Lộc dùng để UX), tránh lệch luật giữa 2 phía.

---

## 8. RỦI RO & LƯU Ý

| Rủi ro | Ảnh hưởng | Đề xuất giảm thiểu |
|---|---|---|
| Luật cờ thú có nhiều biến thể (luật địa phương khác nhau) | Sai lệch trải nghiệm | Thống nhất áp dụng đúng 1 bộ luật quốc tế chuẩn ngay từ đầu, viết test-case rõ ràng |
| Đồng bộ trạng thái giữa nhiều client qua RSocket stream | Trạng thái lệch, bug khó tái hiện | Server luôn là nguồn xác định duy nhất, client không tự suy luận thắng/thua |
| Bot tính toán chậm ảnh hưởng trải nghiệm | Người chơi chờ lâu | Giới hạn độ sâu tìm kiếm, giới hạn thời gian tính nước đi |
| Mất kết nối RSocket giữa ván | Gián đoạn trải nghiệm | Cơ chế reconnect + timeout hợp lý, lưu trạng thái phòng tạm thời |
| Token hết hạn giữa ván đang chơi | Bị văng khỏi phòng | Client refresh qua REST rồi reconnect RSocket bằng access token mới |

---

## 9. TIÊU CHÍ NGHIỆM THU (Definition of Done – tổng quan)

- Người chơi đăng nhập, kết nối RSocket thành công với access token hợp lệ; refresh token hoạt động đúng khi access token hết hạn và client reconnect.
- Có thể tạo/join phòng, chơi đầy đủ 1 ván cờ thú đúng luật quốc tế ở cả 3 chế độ PvP Online, PvE, EvE.
- Server xác định đúng điều kiện thắng/thua/hòa trong mọi trường hợp luật đã liệt kê ở mục 3.
- Bot đưa ra nước đi hợp lệ trong giới hạn thời gian quy định, không bao giờ đi nước phạm luật.
- Giao diện hiển thị đúng animation, âm thanh tương ứng với từng hành động trong ván đấu.
- Ứng dụng xử lý được tình huống mất kết nối tạm thời mà không làm hỏng ván đấu.

---

*Tài liệu này là bản nháp v1.0, cần được rà soát cùng cả team trước khi bắt đầu triển khai chi tiết (thiết kế DB, thiết kế API/message contract RSocket, wireframe UI).*
