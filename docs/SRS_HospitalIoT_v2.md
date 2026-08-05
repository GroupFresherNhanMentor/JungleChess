# SRS – Hệ thống Quản lý Bệnh viện tích hợp IoT (HMS-IoT)
### Kiến trúc Monolith Modular — Team 8 người, mỗi người fullstack 1 module — Thời gian: 1 tuần
### *(Bản v2 — cân bằng lại 8 module: gộp Auth+Core+IoT, gộp Patient+Department, tách riêng module Cận lâm sàng & Xét nghiệm, gộp lại Billing+Dashboard)*

---

## 0. Ghi chú về công nghệ (đã kiểm tra)

| Công nghệ | Tình trạng kiểm tra |
|---|---|
| **Java 25** | Là bản LTS mới nhất, được **khuyến nghị chính thức** cho Spring Boot 4.x (virtual threads hoạt động tốt nhất từ Java 24 trở lên). Dùng được, không có vấn đề tương thích. |
| **Spring Boot 4** | Đã release chính thức (4.0 tháng 11/2025, 4.1 tháng 6/2026, bản build trên Spring Framework 7). Yêu cầu tối thiểu Java 17, **hỗ trợ tốt đến Java 26** → dùng Java 25 hoàn toàn phù hợp. Khuyến nghị dùng **Spring Boot 4.1** (bản đang được support, 4.0 sắp hết hạn support). |
| **HiveMQ** | HiveMQ **không phải là công cụ giả lập thiết bị**, mà là **MQTT Broker** (HiveMQ Community Edition – mã nguồn mở, chạy qua Docker `hivemq/hivemq-ce`). "Thiết bị IoT" là một **Device Simulator** tự viết (dùng `HiveMQ MQTT Client` cho Java) đóng vai trò publisher gửi dữ liệu giả lập vào broker; backend subscribe từ broker để nhận dữ liệu. → Mô hình chuẩn của dự án IoT thực tế (broker + simulator, không có phần cứng thật). |

**Stack chốt cho dự án:**
- Backend: **Java 25 + Spring Boot 4.1** (Spring Framework 7, Jakarta EE)
- IoT: **HiveMQ CE** (MQTT Broker, chạy Docker) + Device Simulator viết bằng Java (HiveMQ MQTT Client library) + Spring Integration MQTT hoặc Eclipse Paho để backend subscribe
- Database: PostgreSQL 16
- Frontend: Angular (bản mới nhất), TailwindCSS/PrimeNG cho UI
- Realtime tới trình duyệt: WebSocket/STOMP
- Auth: JWT (Access + Refresh Token), BCrypt
- Container: Docker Compose (app, db, hivemq-ce)

---

## 1. Giới thiệu

### 1.1 Mục đích
Xây dựng một phần mềm quản lý bệnh viện (Hospital Management System) mô phỏng tương đối sát với nghiệp vụ thực tế: từ tiếp nhận bệnh nhân, đặt lịch/xếp hàng khám, khám bệnh, chỉ định & thực hiện cận lâm sàng, kê đơn, quản lý thuốc/tồn kho dược, nhập viện/xuất viện, đến giám sát chỉ số sinh tồn qua IoT theo thời gian thực và cảnh báo lâm sàng. Đây không phải bản demo tối giản mà mô phỏng đủ các luồng nghiệp vụ chính của một HMS thực tế.

### 1.2 Phạm vi (Scope)
- Kiến trúc **Monolith Modular**: một backend Spring Boot duy nhất, tổ chức theo package-by-feature, một database PostgreSQL duy nhất, một Angular app chia theo feature module.
- Mô phỏng các nghiệp vụ cốt lõi: tiếp nhận & đăng ký khám, quản lý khoa/phòng/giường, hàng đợi khám có phân loại ưu tiên (triage), hồ sơ bệnh án điện tử, **quản lý cận lâm sàng/xét nghiệm như một module riêng có danh mục + trạng thái + kết quả**, kê đơn thuốc gắn với danh mục & tồn kho dược thực tế, nhập viện/xuất viện, hóa đơn viện phí (khám + thuốc + xét nghiệm + giường), giám sát chỉ số sinh tồn IoT, cảnh báo lâm sàng theo rule, dashboard thống kê.
- Không tích hợp phần cứng IoT thật → dùng Device Simulator (Java, HiveMQ MQTT Client) publish dữ liệu qua HiveMQ CE broker.
- Không xử lý bảo hiểm y tế chi tiết, không tích hợp cổng thanh toán thật. Quản lý thuốc chỉ dừng ở mức danh mục + tồn kho cơ bản. Quản lý cận lâm sàng chỉ dừng ở mức danh mục xét nghiệm + trạng thái + kết quả dạng text/file đính kèm đơn giản (không tích hợp máy xét nghiệm thật).
- Đủ **8 module** cho 8 người, mỗi người làm fullstack trọn vẹn 1 module, khối lượng công việc được cân bằng giữa các module.

### 1.3 Đối tượng sử dụng (Actors)
| Actor | Vai trò |
|---|---|
| Lễ tân (Receptionist) | Tiếp nhận bệnh nhân, tạo hồ sơ, xếp hàng khám |
| Bệnh nhân (Patient) | Đặt lịch, xem hồ sơ, xem đơn thuốc, xem kết quả xét nghiệm, xem hóa đơn |
| Bác sĩ (Doctor) | Khám bệnh, chẩn đoán, kê đơn, chỉ định cận lâm sàng, xem kết quả, xem cảnh báo |
| Y tá (Nurse) | Theo dõi vital signs, xử lý cảnh báo, hỗ trợ nhập/xuất viện |
| Kỹ thuật viên XN/CĐHA (Lab Technician) | Thực hiện xét nghiệm/chẩn đoán hình ảnh theo chỉ định, nhập kết quả |
| Dược sĩ (Pharmacist) | Quản lý danh mục thuốc, nhập/xuất kho, theo dõi tồn kho |
| Quản trị viên (Admin) | Quản lý user, khoa/phòng, danh mục xét nghiệm, cấu hình hệ thống, ngưỡng cảnh báo |
| Kế toán viện phí (Billing Staff) | Lập/xử lý hóa đơn, xem báo cáo thống kê |
| Thiết bị IoT (giả lập) | Gửi dữ liệu cảm biến định kỳ qua MQTT |

### 1.4 Từ viết tắt
- SRS: Software Requirements Specification
- HMS: Hospital Management System
- MQTT: Message Queuing Telemetry Transport
- RBAC: Role-Based Access Control
- BPM/SpO2: nhịp tim / độ bão hòa oxy máu
- EMR: Electronic Medical Record (hồ sơ bệnh án điện tử)
- CLS: Cận lâm sàng (xét nghiệm/chẩn đoán hình ảnh)

---

## 2. Tổng quan hệ thống

### 2.1 Kiến trúc tổng thể

```
                        ┌───────────────────────────────────────────────────────────┐
                        │                 Spring Boot 4.1 Monolith                    │
                        │                    (Java 25, single JVM)                    │
                        │                                                             │
                        │  auth(+device)/ patient(+dept)/ medicine/ appointment(+staff)│
                        │  medicalrecord/ lab/ alert/ billing(+dashboard)             │
                        │                                                             │
                        │  ┌───────────────┐      ┌─────────────────────┐            │
                        │  │ MQTT Consumer │◀────▶│  WebSocket Handler   │            │
                        │  │ (Paho/HiveMQ  │      │ (STOMP → Dashboard)  │            │
                        │  │  MQTT Client) │      └─────────────────────┘            │
                        │  └───────┬───────┘                                         │
                        └──────────┼─────────────────────────────────────────────────┘
                                   │ subscribe topic: hospital/vitals/#
                                   ▼
                        ┌───────────────────────┐
                        │  HiveMQ CE (Docker)    │ ◀── publish ── Device Simulator
                        │  MQTT Broker           │                (Java, giả lập N thiết bị)
                        └───────────────────────┘
                                   │
                                   ▼
                        ┌───────────────────────┐
                        │   PostgreSQL 16 (DB)   │
                        └───────────────────────┘
                                   ▲
                                   │ REST API (JWT)
                        ┌───────────────────────┐
                        │     Angular App        │
                        │ (feature module/route  │
                        │  lazy-load theo module) │
                        └───────────────────────┘
```

### 2.2 Công nghệ sử dụng chi tiết
| Layer | Công nghệ | Ghi chú |
|---|---|---|
| Ngôn ngữ | Java 25 | LTS mới nhất, tương thích Spring Boot 4.1 |
| Backend framework | Spring Boot 4.1 (Spring Framework 7) | Web MVC, Validation, Security, Data JPA |
| Database | PostgreSQL 16 | 1 schema, chia bảng theo module |
| IoT Broker | HiveMQ CE (Docker `hivemq/hivemq-ce`) | MQTT 3.1.1/5, broker trung gian |
| IoT Client (backend) | HiveMQ MQTT Client (Java) hoặc Eclipse Paho | Subscribe topic vitals từ broker |
| IoT Simulator | Java app riêng, publish qua HiveMQ MQTT Client | Giả lập N thiết bị đeo bệnh nhân |
| Realtime tới FE | WebSocket/STOMP | Backend relay dữ liệu MQTT → FE |
| Frontend | Angular (bản mới nhất) | Standalone components, lazy-loaded feature route |
| UI Kit | PrimeNG hoặc Angular Material | Table, Calendar, Chart (ngx-charts) |
| Auth | JWT (Access/Refresh) + BCrypt | Spring Security 7 |
| Build tool | Maven hoặc Gradle | Theo thói quen team |
| Container | Docker Compose (app, postgres, hivemq-ce) | 1 lệnh `docker-compose up` chạy toàn bộ |
| API Docs | springdoc-openapi (Swagger UI) | Test API nhanh giữa các module |

### 2.3 Nguyên tắc tổ chức code
- Mỗi module là 1 package riêng biệt: `com.hms.auth` (gồm Core setup + Device/IoT), `com.hms.patient` (gồm Department/Room/Bed), `com.hms.medicine`, `com.hms.appointment` (gồm Staff/Shift), `com.hms.medicalrecord`, `com.hms.lab` (Cận lâm sàng & Xét nghiệm), `com.hms.alert`, `com.hms.billing` (gồm Reporting Dashboard).
- Cấu trúc trong mỗi package: `entity/`, `repository/`, `service/`, `controller/`, `dto/`.
- Package `com.hms.core` (do Dev A phụ trách, setup Ngày 1): `BaseEntity`, `ApiResponse<T>`, `GlobalExceptionHandler`, `SecurityConfig`.
- Frontend: mỗi module là 1 Angular feature folder, route lazy-load riêng, dùng chung `core/` (interceptor JWT, guard theo role) và `shared/` (component dùng chung).
- Quy ước Git: branch `feature/<module-name>`, PR review chéo giữa 2 người trước khi merge `main`, tránh conflict do 8 người code song song.
- **Nguyên tắc gọi module chéo**: chỉ gọi qua interface/service công khai của module kia, không truy cập thẳng Repository của module khác (giữ ranh giới rõ, dễ tách microservice sau này).

---

## 3. Phân rã module & phân công (8 người / 1 tuần, fullstack)

### 3.1 Lý do phân chia (v2)
- **Gộp Auth & User Management + Core setup + Device & Vital Signs (IoT)**: Core/Auth phải xong sớm (Ngày 1–2) rồi rảnh tay, trong khi IoT (Simulator + MQTT + WebSocket) chỉ cần bắt đầu từ Ngày 3 → một người làm nối tiếp 2 việc không bị dồn tải giữa tuần, cũng không bị rảnh.
- **Gộp Patient & Admission + Department/Room/Bed**: Patient/Admission vốn phụ thuộc dữ liệu Dept nhiều nhất (chọn giường trống khi nhập viện) → gộp giảm 1 điểm phụ thuộc chéo, đồng thời bù tải cho việc Auth không còn ôm Dept nữa.
- **Tách riêng module Cận lâm sàng & Xét nghiệm** (trước đây chỉ là 1 FR nhỏ nằm trong Medical Record): nghiệp vụ này có danh mục riêng (loại xét nghiệm, đơn giá), có quy trình trạng thái riêng (Đã chỉ định → Đang thực hiện → Có kết quả), và có vai trò riêng (KTV xét nghiệm) — đủ lớn để làm 1 module độc lập thay vì làm "kèm" trong Medical Record. Việc tách này cũng giúp module Medical Record đỡ ôm 2 luồng nghiệp vụ khác nhau (khám + xét nghiệm).
- **Gộp lại Billing & Reporting Dashboard thành 1 module**: để bù đúng 1 suất nhân sự cho module Cận lâm sàng mới tách ra, đồng thời 2 việc này vốn liên quan chặt (Dashboard cần số liệu doanh thu từ Billing) nên gộp không làm tăng rủi ro phụ thuộc chéo giữa 2 người như các cặp khác.

### 3.2 Bảng phân công

| # | Thành viên | Module | Phạm vi Backend | Phạm vi Frontend |
|---|---|---|---|---|
| 1 | Mạnh | **Auth & Core Setup + Device & Vital Signs (IoT)** | Đăng ký/đăng nhập, JWT, RBAC, quản lý user; setup BaseEntity/ExceptionHandler/SecurityConfig cho cả team; Device Simulator, MQTT Consumer (HiveMQ), lưu vital signs, WebSocket relay | Login/register, quản lý user (Admin), route guard theo role; trang quản lý thiết bị, biểu đồ vital signs realtime |
| 2 | Tín | **Patient & Admission Management + Department/Room/Bed** | CRUD bệnh nhân, nhập/xuất viện, gán giường, gán thiết bị IoT; CRUD khoa/phòng/giường, trạng thái giường | Tiếp nhận bệnh nhân, danh sách nhập viện, form nhập/xuất viện, quản lý khoa phòng, sơ đồ giường |
| 3 | Nguyên | **Medicine & Pharmacy Management** | CRUD danh mục thuốc, tồn kho (nhập/xuất), tự động trừ kho khi kê đơn, cảnh báo sắp hết thuốc | Danh mục thuốc, quản lý tồn kho, lịch sử nhập/xuất |
| 4 | Sơn | **Appointment & Queue Management + Staff & Shift Management** | Đặt lịch, hàng đợi ưu tiên, trạng thái lịch hẹn; CRUD bác sĩ/y tá, ca trực | Trang đặt lịch, calendar, queue board, lịch trực nhân sự |
| 5 | Nghĩa | **Medical Record & Prescription** | CRUD hồ sơ bệnh án, kê đơn (gọi API Medicine), gửi yêu cầu chỉ định cận lâm sàng (gọi API module Lab), xem kết quả trả về | Form khám bệnh + kê đơn + chỉ định CLS, lịch sử khám |
| 6 | Khôi | **Cận lâm sàng & Xét nghiệm (Lab & Diagnostic Management)** *(module mới)* | CRUD danh mục xét nghiệm/CĐHA (tên, loại, đơn giá); nhận yêu cầu từ Medical Record; quản lý trạng thái thực hiện; nhập kết quả; cung cấp đơn giá cho Billing | Danh mục xét nghiệm, hàng đợi xét nghiệm chờ thực hiện, form nhập kết quả, lịch sử xét nghiệm theo bệnh nhân |
| 7 | Thắng | **Alert & Clinical Rule Engine** | Rule threshold theo loại chỉ số, sinh cảnh báo, mức độ (Warning/Critical), escalation | Danh sách cảnh báo realtime, cấu hình ngưỡng cảnh báo |
| 8 | Lộc | **Billing & Reporting Dashboard** | Sinh hóa đơn (khám + thuốc + xét nghiệm + giường theo giá thực từ Medicine/Lab), quản lý hóa đơn; API thống kê tổng hợp, báo cáo theo thời gian/khoa | Trang hóa đơn, dashboard tổng quan, trang báo cáo/thống kê |

---

## 4. Nghiệp vụ chính & liên kết giữa các module

### 4.1 Luồng nghiệp vụ tổng thể (happy path)

```
1. Tiếp nhận          Lễ tân tạo/tìm hồ sơ bệnh nhân               (Patient)
        │
2. Đặt lịch/Xếp hàng  Chọn khoa → bác sĩ (theo ca trực) → giờ khám  (Appointment+Staff)
        │             Check-in → vào hàng đợi theo mức ưu tiên
        ▼
3. Khám bệnh          Bác sĩ tạo hồ sơ khám, chẩn đoán               (Medical Record)
        │
        ├──► 4a. Kê đơn thuốc     → chọn thuốc từ danh mục,          (Medicine)
        │        trừ tồn kho tương ứng
        └──► 4b. Chỉ định CLS     → tạo yêu cầu xét nghiệm/CĐHA,     (Lab)
                 │                  KTV thực hiện, nhập kết quả
                 ▼
            Bác sĩ xem kết quả → hoàn tất chẩn đoán                  (Medical Record ← Lab)
        │
5. Nhập viện (nếu cần) Gán giường (khoa/phòng còn trống)             (Patient+Dept)
        │              Gán thiết bị IoT theo dõi vital signs
        ▼
6. Giám sát IoT       Simulator → HiveMQ → lưu VitalSignRecord       (Auth+IoT)
        │             → relay realtime lên Dashboard
        ▼
7. Cảnh báo           So khớp rule → sinh Alert nếu bất thường       (Alert)
        │             → đẩy realtime cho Nurse/Dashboard, Nurse acknowledge
        ▼
8. Xuất viện          Cập nhật trạng thái, giải phóng giường,        (Patient+Dept)
        │             ghi tóm tắt điều trị
        ▼
9. Hóa đơn            Tính phí khám + tiền thuốc (Medicine) +        (Billing+Dashboard)
                      tiền xét nghiệm (Lab) + tiền giường → xuất hóa đơn
```

### 4.2 Ma trận liên kết giữa các module

| Module nguồn | Module đích | Dữ liệu/Sự kiện trao đổi | Chiều phụ thuộc |
|---|---|---|---|
| Auth+Core+IoT | Tất cả | Xác thực JWT, thông tin user/role | Nền tảng, mọi module đều gọi vào |
| Patient+Dept | — | Chọn giường trống theo khoa (nội bộ module, không còn phụ thuộc Auth) | Đã tự chứa từ khi gộp Dept vào |
| Appointment+Staff | Patient+Dept | Kiểm tra khoa, phòng khám còn hoạt động | Appointment đọc dữ liệu từ Patient+Dept |
| Appointment+Staff | Medical Record | `appointment_id` làm khóa gắn hồ sơ khám | Medical Record phụ thuộc Appointment đã tồn tại |
| Medical Record | Medicine | Chọn thuốc khi kê đơn, yêu cầu trừ tồn kho | Gọi service đồng bộ, trong transaction |
| Medical Record | **Lab (module mới)** | Tạo yêu cầu chỉ định CLS, đọc kết quả trả về | Gọi service đồng bộ khi tạo yêu cầu; đọc lại khi có kết quả |
| **Lab** | Billing+Dashboard | Đơn giá xét nghiệm thực tế theo từng dòng chỉ định | Billing đọc giá từ Lab thay vì số cứng |
| Medicine | Billing+Dashboard | Đơn giá thuốc thực tế theo từng dòng đơn thuốc | Billing đọc giá từ Medicine |
| Patient+Dept | Billing+Dashboard | `admission_id` làm căn cứ tính tiền giường | Billing đọc dữ liệu từ Admission |
| Auth+IoT | Alert | Sự kiện nội bộ `VitalSignReceivedEvent` mỗi khi có dữ liệu mới | Alert lắng nghe (subscribe), không gọi ngược |
| Auth+IoT, Alert | Billing+Dashboard | Số liệu tổng hợp: bệnh nhân đang theo dõi, cảnh báo mở | Dashboard chỉ đọc (read-only aggregation) |

**Nguyên tắc giao tiếp giữa module:** gọi qua Service layer trực tiếp (không HTTP nội bộ), chỉ qua interface công khai; riêng Auth+IoT → Alert dùng Spring ApplicationEvent (bất đồng bộ, publish/subscribe). Các contract quan trọng (Auth↔tất cả, Medical Record↔Medicine, **Medical Record↔Lab**, Auth+IoT↔Alert, Medicine/Lab↔Billing) phải chốt bằng OpenAPI/DTO ngay từ Ngày 1.

### 4.3 Điểm phụ thuộc rủi ro cần lưu ý
- **Medicine và Lab cùng chặn Medical Record**: Dev E phụ thuộc *2* module ngoài (Dev C và Dev F) thay vì 1 như trước — cần chốt contract cả 2 API sớm, dùng mock nếu bên nào chưa xong.
- **Staff/Shift chặn Appointment**: đã gộp chung 1 dev (Dev D) nên không có rủi ro chờ nhau giữa 2 người.
- **Alert phụ thuộc Auth+IoT**: Dev G cần contract sự kiện `VitalSignReceivedEvent` từ Dev A ngay đầu Ngày 3–4.
- **Billing+Dashboard là module phụ thuộc nhiều nhất**: cần dữ liệu từ Admission (Dev B), Prescription (Dev E), Medicine (Dev C), Lab (Dev F) → bắt buộc dùng mock cho phần chưa sẵn sàng, tích hợp thật cuối Ngày 5 – Ngày 6. Đây là module rủi ro tích hợp cao nhất, nên ưu tiên chốt API contract từ các module khác càng sớm càng tốt.

---

## 5. Yêu cầu chức năng chi tiết theo module

### 5.1 Auth & User Management + Core Setup + Device & Vital Signs (IoT)
- FR-1.1: Đăng ký tài khoản theo role (Admin tạo tài khoản cho Doctor/Nurse/Receptionist/Billing/Pharmacist/Lab Technician; Patient tự đăng ký).
- FR-1.2: Đăng nhập, cấp Access Token (JWT) + Refresh Token.
- FR-1.3: Middleware kiểm tra quyền theo role cho từng API (RBAC), trả lỗi 403 nếu không đủ quyền.
- FR-1.4: Admin xem/khóa/mở tài khoản người dùng.
- FR-1.5: Đổi mật khẩu, quên mật khẩu (mô phỏng gửi email dạng log).
- FR-1.6: Device Simulator (Java app riêng) mô phỏng N thiết bị đeo, mỗi thiết bị gắn với 1 bệnh nhân đang theo dõi.
- FR-1.7: Simulator publish dữ liệu mỗi 3–5 giây lên topic `hospital/vitals/{deviceId}` gồm: nhịp tim, SpO2, nhiệt độ, huyết áp.
- FR-1.8: Backend subscribe `hospital/vitals/#` qua HiveMQ MQTT Client, parse và lưu vào `vital_sign_records`.
- FR-1.9: Backend relay dữ liệu qua WebSocket/STOMP tới client Angular đang subscribe theo `patientId`/`deviceId`.
- FR-1.10: Trang quản lý thiết bị: danh sách, trạng thái Online/Offline, gán/gỡ thiết bị khỏi bệnh nhân.

### 5.2 Patient & Admission Management + Department/Room/Bed
- FR-2.1: CRUD hồ sơ bệnh nhân (họ tên, ngày sinh, giới tính, CMND/CCCD, địa chỉ, liên hệ khẩn cấp, tiền sử dị ứng).
- FR-2.2: Tìm kiếm bệnh nhân theo tên/mã bệnh nhân/số điện thoại.
- FR-2.3: Nhập viện: chọn khoa, chọn giường trống, ghi nhận lý do nhập viện.
- FR-2.4: Xuất viện: cập nhật trạng thái, giải phóng giường, ghi nhận ngày xuất viện + tóm tắt điều trị.
- FR-2.5: Gán thiết bị IoT (giả lập) cho bệnh nhân đang nằm viện.
- FR-2.6: CRUD khoa (Nội, Ngoại, Nhi, Cấp cứu, ICU...) và phòng/giường thuộc từng khoa.
- FR-2.7: Theo dõi trạng thái giường (Trống/Đang sử dụng/Bảo trì).

### 5.3 Medicine & Pharmacy Management
- FR-3.1: CRUD danh mục thuốc (tên, hàm lượng, đơn vị tính, đơn giá).
- FR-3.2: Quản lý tồn kho: nhập kho, xuất kho khi có đơn thuốc.
- FR-3.3: Khi Medical Record tạo đơn thuốc, tự động trừ tồn kho tương ứng; trả lỗi nếu không đủ.
- FR-3.4: Cảnh báo thuốc sắp hết hàng (dưới ngưỡng tồn kho tối thiểu).
- FR-3.5: Xem lịch sử nhập/xuất kho theo thuốc, theo khoảng thời gian.

### 5.4 Appointment & Queue Management + Staff & Shift Management
- FR-4.1: Đặt lịch khám: chọn khoa → bác sĩ (theo ca trực) → khung giờ trống.
- FR-4.2: Xác nhận/hủy/dời lịch hẹn.
- FR-4.3: Hàng đợi khám trong ngày (Queue Board) có mức ưu tiên (Khẩn cấp/Bình thường) — bệnh nhân ưu tiên cao được gọi trước dù đến sau.
- FR-4.4: Trạng thái lịch hẹn: Chờ khám → Đang khám → Hoàn tất/Hủy.
- FR-4.5: CRUD bác sĩ/y tá, gán vào khoa, chuyên môn.
- FR-4.6: Quản lý lịch trực/ca làm việc theo tuần, dùng để kiểm tra khung giờ trống khi đặt lịch (FR-4.1).

### 5.5 Medical Record & Prescription
- FR-5.1: Bác sĩ tạo hồ sơ khám gắn với lịch hẹn: triệu chứng, chẩn đoán, ghi chú lâm sàng.
- FR-5.2: Kê đơn thuốc: chọn thuốc từ danh mục (gọi API module Medicine), nhập liều dùng/số ngày; hệ thống tự trừ tồn kho tương ứng.
- FR-5.3: Chỉ định cận lâm sàng: chọn loại xét nghiệm từ danh mục (đọc từ module Lab), gửi yêu cầu tạo `LabOrder` sang module Lab; khi có kết quả, đọc lại và hiển thị trong hồ sơ khám.
- FR-5.4: Xem lịch sử khám đầy đủ của một bệnh nhân (timeline gồm cả đơn thuốc và kết quả CLS).

### 5.6 Cận lâm sàng & Xét nghiệm (Lab & Diagnostic Management) *(module mới)*
- FR-6.1: CRUD danh mục xét nghiệm/CĐHA (tên, loại: Xét nghiệm máu/nước tiểu/Chẩn đoán hình ảnh..., đơn giá, thời gian trả kết quả dự kiến).
- FR-6.2: Nhận yêu cầu chỉ định từ module Medical Record (API tạo `LabOrder` gắn `medical_record_id` + `test_catalog_id`).
- FR-6.3: Danh sách xét nghiệm đang chờ thực hiện theo trạng thái: Đã chỉ định → Đang thực hiện → Đã có kết quả; KTV cập nhật trạng thái.
- FR-6.4: Nhập kết quả xét nghiệm (chỉ số + đơn vị + khoảng bình thường dạng text, hoặc upload file CĐHA đơn giản).
- FR-6.5: Cung cấp API cho Medical Record đọc kết quả khi hoàn tất, và cung cấp đơn giá xét nghiệm cho module Billing.
- FR-6.6: Xem lịch sử xét nghiệm theo bệnh nhân.

### 5.7 Alert & Clinical Rule Engine
- FR-7.1: Admin cấu hình ngưỡng cảnh báo theo từng loại chỉ số (VD: BPM > 120 hoặc < 50 → Critical; SpO2 < 92% → Critical; nhiệt độ > 39°C → Warning).
- FR-7.2: Khi nhận dữ liệu vital sign mới (qua Spring Event từ module Auth+IoT), kiểm tra theo rule đã cấu hình.
- FR-7.3: Sinh bản ghi Alert (mức độ, thông điệp, thời gian), lưu DB.
- FR-7.4: Đẩy cảnh báo realtime qua WebSocket tới màn hình Nurse/Dashboard.
- FR-7.5: Cơ chế escalation: nếu cảnh báo Critical không được acknowledge trong X phút, đánh dấu "Chưa xử lý" nổi bật.
- FR-7.6: Y tá "Acknowledge" (xác nhận đã xử lý) một cảnh báo.

### 5.8 Billing & Reporting Dashboard
- FR-8.1: Sinh hóa đơn viện phí gồm: phí khám, tiền thuốc (giá thực từ Medicine), **tiền xét nghiệm/CLS (giá thực từ Lab)**, tiền giường (số ngày × đơn giá theo khoa).
- FR-8.2: Xem/lọc danh sách hóa đơn theo bệnh nhân, theo trạng thái (Chưa thanh toán/Đã thanh toán).
- FR-8.3: Đánh dấu hóa đơn đã thanh toán (mô phỏng, không tích hợp cổng thanh toán thật).
- FR-8.4: Xem chi tiết hóa đơn (breakdown từng dòng: phí khám / từng dòng thuốc / từng dòng xét nghiệm / tiền giường), có bản in/PDF đơn giản.
- FR-8.5: Dashboard tổng quan: tổng bệnh nhân đang điều trị, số lịch hẹn hôm nay, số cảnh báo Critical đang mở, tỷ lệ giường trống theo khoa, doanh thu tạm tính trong ngày/tuần, số thuốc sắp hết hàng.
- FR-8.6: Biểu đồ vital signs tổng hợp và danh sách cảnh báo mới nhất (đọc dữ liệu tổng hợp từ module Auth+IoT/Alert).
- FR-8.7: Báo cáo theo khoảng thời gian (ngày/tuần/tháng): số lượt khám, số ca nhập viện, doanh thu; thống kê theo khoa (số bệnh nhân, tỷ lệ lấp đầy giường, doanh thu).
- FR-8.8: Xuất báo cáo dạng CSV/Excel cho các bảng thống kê chính.

---

## 6. Yêu cầu phi chức năng
- **Hiệu năng**: dữ liệu vital sign từ Simulator → HiveMQ → Backend → Dashboard có độ trễ tổng < 2 giây.
- **Tính module hóa**: dù là monolith, code tách rõ theo package để 8 người làm song song không conflict nhiều.
- **Bảo mật**: JWT cho mọi API (trừ endpoint auth), mã hóa mật khẩu BCrypt, phân quyền RBAC chặt theo role ở tầng Controller/Service.
- **Khả dụng**: chấp nhận downtime khi demo, không yêu cầu high-availability.
- **Khả năng kiểm thử**: mỗi module có ít nhất unit test cho service layer chính; test tích hợp cơ bản cho luồng IoT end-to-end.
- **Khả năng quan sát**: log đầy đủ ở tầng Controller, log riêng cho MQTT Consumer.
- **Toàn vẹn dữ liệu**: thao tác trừ kho khi kê đơn (FR-3.3/FR-5.2) và tạo yêu cầu xét nghiệm/nhập kết quả phải đảm bảo transaction — không để dữ liệu nửa vời nếu 1 bước thất bại.

---

## 7. Mô hình dữ liệu chi tiết (ERD tóm tắt)

**Entities chính:**
- `User` (id, username, password_hash, role, active)
- `Patient` (id, user_id, full_name, dob, gender, national_id, address, emergency_contact, allergy_note)
- `Department` (id, name, type)
- `Room` (id, department_id, room_number)
- `Bed` (id, room_id, bed_number, status: EMPTY/OCCUPIED/MAINTENANCE)
- `Admission` (id, patient_id, bed_id, admitted_at, discharged_at, reason, discharge_summary, status)
- `Staff` (id, user_id, full_name, role: DOCTOR/NURSE, department_id, specialty)
- `Shift` (id, staff_id, day_of_week, start_time, end_time)
- `Appointment` (id, patient_id, doctor_id, department_id, time_slot, priority: NORMAL/URGENT, status: WAITING/IN_PROGRESS/DONE/CANCELLED)
- `MedicalRecord` (id, appointment_id, symptoms, diagnosis, notes, status: DRAFT/FINALIZED, created_at)
- `Medicine` (id, name, dosage_form, unit, unit_price, stock_quantity, min_stock_threshold)
- `StockTransaction` (id, medicine_id, type: IMPORT/EXPORT, quantity, reference_prescription_id, created_at)
- `Prescription` (id, medical_record_id, medicine_id, dosage, duration_days, quantity)
- `LabTestCatalog` (id, name, type, unit_price, expected_turnaround_minutes) *(mới)*
- `LabOrder` (id, medical_record_id, test_catalog_id, status: ORDERED/IN_PROGRESS/RESULTED, result_text, result_file_url, ordered_at, resulted_at) *(cập nhật: thêm `test_catalog_id`, `result_file_url`)*
- `Device` (id, patient_id, device_code, status: ONLINE/OFFLINE, last_seen_at)
- `VitalSignRecord` (id, device_id, type: HEART_RATE/SPO2/TEMPERATURE/BLOOD_PRESSURE, value, recorded_at)
- `AlertRule` (id, vital_type, min_threshold, max_threshold, level: WARNING/CRITICAL)
- `Alert` (id, device_id, patient_id, rule_id, level, message, acknowledged, created_at)
- `Invoice` (id, patient_id, admission_id, exam_fee, medicine_fee, **lab_fee**, bed_fee, total, status: UNPAID/PAID, created_at) *(cập nhật: thêm `lab_fee`)*

**Quan hệ chính:** Patient 1–N Admission; Admission N–1 Bed; Bed N–1 Room; Room N–1 Department; Patient 1–N Appointment; Appointment 1–1 MedicalRecord; MedicalRecord 1–N Prescription/LabOrder; Prescription N–1 Medicine; Medicine 1–N StockTransaction; **LabTestCatalog 1–N LabOrder**; Patient 1–N Device; Device 1–N VitalSignRecord; Device/Patient 1–N Alert; Patient 1–N Invoice.

---

## 8. Luồng dữ liệu IoT chi tiết (End-to-end qua HiveMQ)

```
Device Simulator (Java, N thiết bị ảo)
        │  MQTT PUBLISH → topic: hospital/vitals/{deviceId}
        ▼
HiveMQ CE Broker (Docker, port 1883)
        │  MQTT SUBSCRIBE (wildcard: hospital/vitals/#)
        ▼
Backend – MQTT Consumer (module Auth+IoT)
        │  parse payload (JSON: deviceId, type, value, timestamp)
        │  lưu VitalSignRecord vào PostgreSQL
        │  publish Spring ApplicationEvent nội bộ: VitalSignReceivedEvent
        ▼
Alert Service (lắng nghe VitalSignReceivedEvent)
        │  so khớp với AlertRule theo vital_type
   ┌────┴─────┐
Bình thường     Bất thường
(không tạo gì)  (tạo Alert, lưu DB)
                     │
                     ▼
        WebSocket/STOMP broadcast tới:
        - Topic /topic/vitals/{patientId}  → biểu đồ realtime (module Auth+IoT FE)
        - Topic /topic/alerts               → danh sách cảnh báo (module Alert FE, Dashboard)
```

**Định dạng message MQTT (ví dụ JSON payload):**
```json
{
  "deviceId": "DEV-001",
  "type": "HEART_RATE",
  "value": 132,
  "timestamp": "2026-08-03T10:15:30Z"
}
```

---

## 9. Chi tiết quy trình khám bệnh & cận lâm sàng (mở rộng Module 5 + Module 6)

### 9.1 Actor & phạm vi
- **Module 5 (Medical Record, Dev E)**: Bác sĩ khám, chẩn đoán, kê đơn, gửi yêu cầu CLS.
- **Module 6 (Lab, Dev F)**: KTV xét nghiệm thực hiện & trả kết quả.
- **Input**: 1 `Appointment` ở trạng thái `WAITING`/`IN_PROGRESS`.
- **Output**: 1 `MedicalRecord` hoàn chỉnh, kèm 0..N `Prescription` và 0..N `LabOrder` (do module Lab quản lý vòng đời).

### 9.2 Sơ đồ quy trình (happy path + nhánh rẽ)

```
[Hàng đợi khám – Appointment: WAITING]
        │  Bác sĩ bấm "Gọi bệnh nhân tiếp theo" (theo priority)
        ▼
[Appointment: IN_PROGRESS]
        │  Load hồ sơ bệnh nhân + lịch sử khám trước (FR-5.4)
        ▼
[Nhập Triệu chứng + Khám lâm sàng + Ghi chú]  →  MedicalRecord (DRAFT)
        │
        ├── Nhánh A: Đủ thông tin chẩn đoán ngay
        │        ▼
        │   [Nhập Chẩn đoán]
        │        ├──► Kê đơn thuốc? ──Có──► gọi API Medicine → kiểm tra tồn kho
        │        │                          → đủ: tạo Prescription + trừ kho (transaction)
        │        │                          → không đủ: báo lỗi, đổi thuốc/số lượng
        │        └──► Cần nhập viện? ──Có──► đề xuất nhập viện (module Patient+Dept)
        │
        └── Nhánh B: Cần cận lâm sàng trước khi chẩn đoán chắc chắn
                 ▼
            [Chọn loại XN từ danh mục Lab] → gọi API module Lab
                 │      tạo LabOrder (status: ORDERED) — vòng đời từ đây do module Lab quản lý
                 ▼
        ── (ranh giới Module 5 / Module 6) ──
                 ▼
            [Module Lab] KTV nhận yêu cầu → cập nhật IN_PROGRESS
                 │  KTV thực hiện xét nghiệm/CĐHA
                 ▼
            [Module Lab] KTV nhập kết quả → status RESULTED
                 │  (đơn giá xét nghiệm sẵn sàng cho Billing)
        ── (quay lại Module 5) ──
                 ▼
            Bác sĩ mở lại MedicalRecord → đọc kết quả (qua API Lab) → quay lại Nhánh A
        │
        ▼
[Bác sĩ bấm "Hoàn tất khám"] → MedicalRecord: FINALIZED, Appointment: DONE
        │
        ▼
Dữ liệu sẵn sàng cho: Billing (phí khám + thuốc + xét nghiệm), Patient timeline
```

### 9.3 State machine liên quan

| Entity | Module chủ quản | Trạng thái | Chuyển đổi |
|---|---|---|---|
| `Appointment` | Appointment+Staff (Dev D) | `WAITING → IN_PROGRESS → DONE` (hoặc `CANCELLED`) | `IN_PROGRESS` khi gọi khám; `DONE` khi hoàn tất. |
| `MedicalRecord` | Medical Record (Dev E) | `DRAFT → FINALIZED` | `FINALIZED` sau khi bấm "Hoàn tất khám" — không sửa được chẩn đoán/đơn thuốc sau mốc này. |
| `LabOrder` | **Lab (Dev F)** | `ORDERED → IN_PROGRESS → RESULTED` | Module Lab toàn quyền cập nhật; Medical Record chỉ tạo yêu cầu ban đầu và đọc kết quả cuối. |
| `Prescription` | Medical Record (ghi) / Medicine (trừ kho) | *(không trạng thái riêng)* | Tạo Prescription + trừ kho phải cùng 1 transaction. |

### 9.4 Màn hình cần có

**Module 5 (Dev E):**
1. Queue board của bác sĩ (lọc theo bác sĩ đăng nhập).
2. Màn hình khám bệnh: panel hồ sơ bệnh nhân | form triệu chứng/chẩn đoán | tab kê đơn (gọi Medicine) | tab chỉ định CLS (gọi Lab, chọn danh mục).
3. Timeline lịch sử khám (đơn thuốc + kết quả CLS).

**Module 6 (Dev F):**
4. Trang danh mục xét nghiệm/CĐHA (CRUD, dành cho Admin/KTV).
5. Hàng đợi xét nghiệm chờ thực hiện (theo trạng thái).
6. Form nhập kết quả (chỉ số + đơn vị, hoặc upload file).
7. Lịch sử xét nghiệm theo bệnh nhân.

### 9.5 Edge case cần xử lý
- Bệnh nhân không đến dù đã gọi → nút "Bỏ qua/No-show", Appointment `CANCELLED`, không tạo MedicalRecord.
- Tái khám trong ngày (chờ kết quả CLS xong quay lại) → mở lại MedicalRecord `DRAFT` cũ, không tạo Appointment mới.
- Kê đơn thất bại do hết tồn kho giữa chừng → trả lỗi rõ ràng, không lưu Prescription.
- Nhiều bác sĩ cùng chỉ định XN cho 1 bệnh nhân trong ngày → mỗi `LabOrder` độc lập theo `medical_record_id`, không gộp.
- KTV nhập nhầm kết quả sau khi đã `RESULTED` → cho phép sửa nhưng ghi log/`updated_at`, module Lab tự quyết định policy chi tiết khi cài đặt.
- Bác sĩ chỉ định CLS nhưng chưa có kết quả mà đã bấm "Hoàn tất khám" → hệ thống cảnh báo xác nhận (vẫn cho phép hoàn tất, LabOrder tồn tại độc lập, kết quả xem sau trong timeline).

---

## 10. Kế hoạch triển khai 1 tuần (chi tiết theo ngày)

| Ngày | Công việc chính | Ghi chú |
|---|---|---|
| **Ngày 1** | Dev A: setup core (BaseEntity, SecurityConfig, GlobalExceptionHandler, ApiResponse) + Auth cơ bản. Dev B: entity Department/Room/Bed + Patient. Cả team thống nhất ERD chi tiết, API contract (OpenAPI) cho từng module — **đặc biệt chốt contract Medical Record ↔ Medicine và Medical Record ↔ Lab ngay hôm nay**. Setup Docker Compose (postgres + hivemq-ce). | Bắt buộc xong trước khi module khác code entity phụ thuộc |
| **Ngày 2** | Dev B hoàn thiện Patient/Admission. Dev D bắt đầu Staff/Shift rồi Appointment. Dev C bắt đầu Medicine — không phụ thuộc ai, chạy độc lập. **Dev F bắt đầu danh mục xét nghiệm (Lab)** — cũng không phụ thuộc ai, chạy độc lập từ đầu như Dev C. | Dev C và Dev F đều không bị block, tận dụng chạy sớm |
| **Ngày 3** | Dev D hoàn thiện Appointment (queue, priority). Dev E bắt đầu Medical Record — dùng mock cho Medicine/Lab nếu 2 module đó chưa xong API. Dev A chuyển sang Device Simulator + kết nối HiveMQ CE. | Test thử luồng MQTT end-to-end (chưa cần UI) |
| **Ngày 4** | Dev A hoàn thiện lưu VitalSignRecord + WebSocket relay. Dev G bắt đầu Alert Service dựa trên event từ Dev A. Dev E tích hợp thật với API Medicine (Dev C) và API Lab (Dev F). Dev F hoàn thiện luồng nhận yêu cầu + trạng thái + nhập kết quả. | Alert phụ thuộc trực tiếp Dev A — cần contract rõ đầu ngày |
| **Ngày 5** | Dev G hoàn thiện Alert + escalation + FE cảnh báo. Dev H bắt đầu Billing (hóa đơn cơ bản, dùng mock giá XN/thuốc nếu cần) và Dashboard. | Billing+Dashboard cần dữ liệu thật từ 4 module khác — nên dùng mock trước |
| **Ngày 6** | Tích hợp toàn bộ 8 module; test luồng chính end-to-end: tiếp nhận → đặt lịch → khám → (kê đơn + chỉ định CLS) → nhập viện → theo dõi IoT → cảnh báo → xuất viện → hóa đơn. Dev H tích hợp API thật từ Billing sang Dashboard. | Ngày rủi ro cao nhất, ưu tiên fix blocking bug |
| **Ngày 7** | Kiểm thử tổng thể, seed dữ liệu demo thực tế, chuẩn bị kịch bản demo, rehearsal thuyết trình. | Chuẩn bị 1 kịch bản demo xuyên suốt toàn bộ luồng nghiệp vụ |

---

## 11. Rủi ro & Giả định
- **Giả định**: Không có thiết bị IoT thật; toàn bộ dữ liệu sinh từ Device Simulator qua HiveMQ CE.
- **Rủi ro phụ thuộc chéo**: Medical Record phụ thuộc *cả* Medicine và Lab (2 module ngoài) → chốt API contract/mock data từ Ngày 1. Billing+Dashboard phụ thuộc nhiều nhất (Admission, Prescription, Medicine, Lab) → module rủi ro tích hợp cao nhất, ưu tiên mock sớm.
- **Rủi ro conflict code**: 8 người cùng code 1 monolith → tách package rõ ràng, PR review chéo.
- **Rủi ro kỹ thuật HiveMQ**: nếu team chưa quen MQTT, dành riêng nửa ngày 3 để Dev A làm proof-of-concept publish/subscribe trước khi tích hợp sâu.
- **Rủi ro tồn kho**: nếu Medicine chưa xong khi Medical Record cần tích hợp, dùng danh mục thuốc mock trước, bổ sung tích hợp thật trước ngày 6.
- **Rủi ro Lab**: nếu module Lab chưa xong API khi Medical Record cần chỉ định CLS, dùng danh mục xét nghiệm mock trước, tích hợp thật trước ngày 6.
- **Giảm thiểu chung**: ưu tiên chạy được luồng "happy path" xuyên suốt trước, bổ sung edge case sau nếu còn thời gian.

---

## 12. Tiêu chí nghiệm thu (Acceptance Criteria)
- [ ] Đăng ký/đăng nhập, phân quyền RBAC hoạt động đúng cho tất cả role.
- [ ] Luồng tiếp nhận bệnh nhân → đặt lịch → xếp hàng đợi theo mức ưu tiên → khám bệnh hoạt động đầy đủ.
- [ ] Kê đơn thuốc chọn được từ danh mục thực tế và tự động trừ tồn kho đúng số lượng; cảnh báo hiển thị khi thuốc sắp hết.
- [ ] Chỉ định cận lâm sàng tạo được yêu cầu sang module Lab, KTV cập nhật trạng thái và nhập kết quả, bác sĩ xem lại được kết quả trong hồ sơ khám.
- [ ] Luồng nhập viện → gán giường → theo dõi vital signs realtime → cảnh báo khi bất thường → xuất viện → sinh hóa đơn hoạt động đầy đủ.
- [ ] Device Simulator publish dữ liệu qua HiveMQ CE, backend nhận và lưu đúng, độ trễ tới Dashboard < 2 giây.
- [ ] Alert Service sinh cảnh báo đúng theo ngưỡng cấu hình, y tá acknowledge được cảnh báo.
- [ ] Hóa đơn tính đúng tiền thuốc (từ Medicine) và tiền xét nghiệm (từ Lab) theo đơn giá thực tế.
- [ ] Dashboard hiển thị đúng số liệu tổng quan và báo cáo theo thời gian/khoa, xuất được CSV.
- [ ] Toàn bộ hệ thống (backend + frontend + PostgreSQL + HiveMQ CE) chạy được bằng `docker-compose up` không cần cấu hình thủ công thêm.

---

*Tài liệu SRS v2 này mô phỏng đầy đủ các luồng nghiệp vụ của một phần mềm quản lý bệnh viện thực tế, với 8 module được cân bằng lại về khối lượng công việc: Auth+Core+IoT, Patient+Department, Medicine, Appointment+Staff, Medical Record, Cận lâm sàng & Xét nghiệm (module mới), Alert, và Billing+Dashboard — tổ chức theo kiến trúc Monolith Modular để 8 thành viên triển khai fullstack song song trong 1 tuần.*
