# INFRA.md — Hạ tầng Backend Lori (Cloud Demo, chi phí $0)

> Tài liệu này ghi lại quy trình dựng hạ tầng Backend của Lori để cả team tham khảo/tái sử dụng.
> **Repo này là Public:** tài liệu chỉ ghi *tên* biến/secret, tuyệt đối không ghi giá trị, chuỗi kết nối, mật khẩu hay token.
> Hạn mức miễn phí bên dưới lấy từ tài liệu của từng nhà cung cấp, có thể thay đổi — kiểm tra lại khi cần.

## 1. Tổng quan

```
Android App
   ↓ HTTPS
Spring Boot Backend — Render (Web Service, Docker)
   ↓ TLS                       ↓ TLS
PostgreSQL — Neon           Redis — Upstash

GitHub Actions (repo lori-db-backup, riêng tư) --pg_dump hàng tuần--> Neon
UptimeRobot --kiểm tra mỗi 5 phút--> Render
```

| Thành phần | Dịch vụ | Gói | Ghi chú giới hạn |
|---|---|---|---|
| Backend | Render Web Service | Free (không cần thẻ) | Tự ngủ sau 15 phút không có truy cập; 750 giờ chạy/tháng cho cả workspace |
| PostgreSQL | Neon | Free (không cần thẻ) | 100 CU-giờ/tháng, 0,5 GB/project, compute tự ngủ sau 5 phút, khôi phục theo thời điểm chỉ 6 giờ |
| Redis | Upstash | Free (không cần thẻ) | — |
| CI/CD + Backup | GitHub Actions | Free | — |
| Giám sát | UptimeRobot | Free | 50 monitor, kiểm tra mỗi 5 phút |
| Audio đề thi | Supabase Storage | Free (không cần thẻ) | 1 GB lưu trữ, 50 MB/file, 5 GB egress; project bị tạm dừng sau 1 tuần không hoạt động |

> Kế hoạch gốc dùng Google Cloud Run; vì team không dùng thẻ tín dụng nên Backend chạy trên **Render**.

## 2. Neon (PostgreSQL)

1. Tạo tài khoản tại https://neon.tech (không cần thẻ tín dụng).
2. Tạo project, trong đó có database `lori_db`. Phiên bản Postgres: **16**.
3. Neon có 2 loại chuỗi kết nối:
   - **Pooled** (host có đuôi `-pooler`): dùng cho ứng dụng nhiều kết nối ngắn.
   - **Direct** (host không có `-pooler`): dùng cho công cụ quản trị như `pg_dump`/`pg_restore`.
4. Kiểm tra kết nối từ máy local bằng `psql` với chuỗi kết nối của `lori_db`.
5. Chuỗi kết nối chứa mật khẩu → chỉ để trong biến môi trường Render và GitHub Secret, không commit.
6. **User ứng dụng:** Backend không dùng user mặc định của Neon (thuộc `neon_superuser`) mà dùng user riêng `lori_app`, tạo bằng SQL (user tạo bằng SQL không thuộc `neon_superuser`), chỉ có quyền `CONNECT` và `USAGE, CREATE` trên schema `public` của `lori_db`.
7. **Chuỗi kết nối của Backend:** khác với gợi ý ở mục 3, Backend dùng host **direct** (không `-pooler`) vì Flyway chạy migration khi app khởi động; định dạng JDBC `jdbc:postgresql://<host>/lori_db?sslmode=require`, user và mật khẩu để ở biến riêng. Pool kết nối đặt `spring.datasource.hikari.minimum-idle=0` để Backend không giữ sẵn kết nối, giúp Neon vẫn tự ngủ khi không có request.

## 3. Upstash (Redis)

1. Tạo tài khoản tại https://upstash.com (không cần thẻ tín dụng).
2. Tạo Redis database; lấy chuỗi kết nối (kết nối qua TLS).
3. Kiểm tra kết nối từ máy local bằng `redis-cli`.
4. Chuỗi kết nối chứa token/mật khẩu → chỉ để trong biến môi trường Render, không commit.

## 4. Render (Backend)

- Loại: **Web Service**, Runtime **Docker**, Root Directory = `backend`, URL: **https://lori-25ej.onrender.com**.
- `backend/Dockerfile` là multi-stage: giai đoạn build dùng Maven + JDK 17, giai đoạn chạy dùng JRE 17.
- Cổng: Render truyền biến `PORT`; `application.properties` dùng `server.port=${PORT:8080}` (chạy local mặc định 8080).
- Biến môi trường cấu hình trong Render → service → **Environment** (chỉ ghi tên, không ghi giá trị). Mỗi biến một dòng, đúng theo thực tế đang có trên Render (xóa/thêm dòng cho khớp):

| Tên biến | Ý nghĩa |
|---|---|
| `NEON_URL` | Chuỗi JDBC tới PostgreSQL (Neon), dạng direct, không chứa user/mật khẩu |
| `NEON_USER` | User ứng dụng của Neon (`lori_app`) |
| `NEON_PASSWORD` | Mật khẩu của user ứng dụng |
| `UPSTASH_URL` | Kết nối Redis (Upstash) |
| `JWT_SECRET` | Khóa ký JWT, chuỗi ngẫu nhiên ≥ 32 byte (256-bit) |
| `SPRING_DATA_REDIS_URL` |              |
| `API_KEY` | Giá trị Backend yêu cầu ở header `X-API-Key` (lớp anti-abuse, không phải ranh giới bảo mật thật — kế hoạch mục 8.0) |
| `APP_SIGNATURE` | Giá trị Backend yêu cầu ở header `X-App-Signature` (SHA-256 chữ ký app) |
| `GOOGLE_CLIENT_ID` | Web client ID của Google OAuth; Backend dùng để kiểm tra `aud` của Google ID Token |
| `BREVO_API_KEY` | API key (v3) của Brevo để Backend gửi email đặt lại mật khẩu (xem mục 10) |
| `MAIL_SENDER_EMAIL` | Email người gửi đã được xác minh trên Brevo (xem mục 10) |

- Auto-Deploy (Render → Settings → Build & Deploy): **Off**.

## 5. CI/CD (GitHub Actions → Render)

File: `.github/workflows/backend-deploy.yml`, tên workflow **Backend CI/CD**.

- **Kích hoạt:** push lên nhánh `main` có thay đổi trong `backend/**` hoặc chính file workflow. Thay đổi ở nơi khác (ví dụ `INFRA.md`, thư mục `app/`) **không** kích hoạt deploy.
- **Job `build`:** `docker build` thư mục `backend` để bắt lỗi build sớm.
- **Job `deploy`** (chạy sau `build`): gọi **Deploy Hook** của Render bằng `curl -X POST`.
- **GitHub Secret (repo Lori):** `RENDER_DEPLOY_HOOK_URL` — URL Deploy Hook lấy từ Render → service → Settings. Không để URL này trong file.
- **Kiểm tra:** push một thay đổi nhỏ trong `backend/` → tab Actions của repo Lori chạy xanh → Render hiện một lần deploy mới.


## 6. Backup Neon tự động (GitHub Actions)

Neon Free chỉ có khôi phục theo thời điểm trong 6 giờ, **không phải backup dài hạn** → có thêm workflow `pg_dump` hàng tuần.

- **Nơi lưu:** repo GitHub **riêng tư** `lori-db-backup`. Repo này (Lori) là Public nên **không bao giờ** để file dump hoặc chuỗi kết nối ở đây.
- **Workflow:** `.github/workflows/neon-backup.yml` nằm trong repo `lori-db-backup` (dùng `GITHUB_TOKEN` có sẵn, không cần token cá nhân).
- **Lịch:** thứ Bảy 20:17 UTC (03:17 sáng Chủ nhật giờ Việt Nam), `cron: '17 20 * * 6'`. Có thể bấm **Run workflow** để chạy tay. GitHub có thể chạy trễ vài phút đến vài chục phút khi hệ thống bận.
- **GitHub Secret (repo `lori-db-backup`):** `NEON_DIRECT_URL` — chuỗi kết nối Neon dạng **direct** (host không có `-pooler`) của `lori_db`.
- **Phiên bản công cụ:** workflow chạy `pg_dump` trong container `postgres:16` (biến `NEON_PG_MAJOR` trong file workflow). `pg_dump` phải **cùng hoặc mới hơn** phiên bản Postgres của Neon → nếu Neon nâng/đổi phiên bản major, phải sửa số này.
- **Kết quả:** mỗi lần chạy tạo file `backups/lori_db_<ngày>_<giờ>.dump` (định dạng custom `-Fc`, tạo bằng `--no-owner --no-acl`), được kiểm tra bằng `pg_restore --list` trước khi commit.

## 7. Giám sát (UptimeRobot)

- Dịch vụ ngoài, độc lập với hạ tầng được theo dõi; tài khoản Free, không cần thẻ.
- Monitor: `Lori Backend (Render)`, loại HTTP(s), kiểm tra mỗi 5 phút, báo qua email.
- URL theo dõi: `https://lori-25ej.onrender.com/actuator/health/ping`.
- **Vì sao không theo dõi `/actuator/health`:** endpoint đó kiểm tra cả PostgreSQL và Redis; gọi mỗi 5 phút sẽ giữ Neon thức gần như liên tục và làm cạn hạn mức 100 CU-giờ/tháng. `/actuator/health/ping` luôn trả `UP`, không đụng DB/Redis.
- **Lưu ý:** monitor 5 phút/lần giữ Render luôn thức (~744/750 giờ miễn phí mỗi tháng) → không chạy thêm service Free thứ hai trong cùng workspace Render.
- `SecurityConfig`, `ApiKeyFilter` và `RateLimitFilter` của Backend phải luôn bỏ qua `/actuator/health/**` (UptimeRobot không gửi `X-API-Key`); nếu không, UptimeRobot sẽ nhận 401/403/429 và báo Down. Danh sách đường dẫn mở nằm ở `SecurityPaths`.


## 8. Database migration (Flyway)

- Migration nằm ở `backend/src/main/resources/db/migration/`, đặt tên `V<số>__<mô_tả>.sql`.
- Flyway tự chạy khi Backend khởi động (cả local lẫn Render), dùng chung kết nối `NEON_URL`.
- `V1__init_schema.sql`: `users`, `refresh_tokens`, `user_progress`, `subscriptions`.
- `V2__exam_tables.sql`: `exam_papers`, `exam_sections`, `exam_questions`, `exam_results`.
- **Không sửa migration đã chạy** (Flyway kiểm tra checksum); muốn đổi schema thì tạo migration mới.


## 9. Đăng nhập Google (OAuth 2.0)

- Dịch vụ: Google Cloud Console → **Google Auth Platform**, dùng project riêng cho Lori. **Không bật thanh toán, không cần thẻ tín dụng** (tạo OAuth client không yêu cầu billing).
- **Audience:** External, trạng thái **Testing** → chỉ các tài khoản Google nằm trong danh sách **Test users** mới đăng nhập được. Chuyển sang In production là quyết định ở giai đoạn phát hành.
- **Web client:** Backend chỉ cần **Client ID** của Web client để kiểm tra trường `aud` của Google ID Token. **Client secret không dùng trong Backend**, không lưu vào repo hay biến môi trường Render.
- **Redirect URI** của Web client: `https://developers.google.com/oauthplayground` — chỉ để lấy ID Token thử bằng tay (OAuth 2.0 Playground).
- **Android client** (cần package name + SHA-1) sẽ được tạo ở Tuần 6, khi làm Google Sign-In trên app.
- Biến môi trường Render: `GOOGLE_CLIENT_ID`.

## 10. Gửi email (Brevo)

- Dùng để gửi mã xác nhận quên mật khẩu. Gói **Free**, **không cần thẻ tín dụng**.
- **Gọi qua HTTPS API** (`POST https://api.brevo.com/v3/smtp/email`, header `api-key`), không dùng SMTP vì **Render Free chặn các cổng SMTP 25, 465, 587**.
- Người gửi (`MAIL_SENDER_EMAIL`) phải được **xác minh** trong Brevo → Senders, Domains & Dedicated IPs → Senders.
- Brevo có tính năng chặn IP lạ gọi API (Settings → Security → Authorized IPs). Vì không kiểm soát được IP gửi đi của Render Free nên đã **tắt chặn (Deactivate blocking)**; bù lại API key chỉ lưu trong biến môi trường, không commit.
- Biến môi trường Render: `BREVO_API_KEY`, `MAIL_SENDER_EMAIL`.

## 11. Dữ liệu đề thi (TOEIC / IELTS)

- Nội dung đề (`exam_papers`, `exam_sections`, `exam_questions`) **không nằm trong repo** và không đi qua Flyway. Dữ liệu được nạp thẳng vào Neon bằng file SQL do script trong `tools/exam-import/` sinh ra. Lý do: repo này là Public và nguồn đề TOEIC không có giấy phép.
- **Nguồn:**
  - TOEIC: `tmd22121999/thi_toeic` (repo nguồn không có tệp giấy phép; dữ liệu chỉ nằm trong database, không commit).
  - IELTS: [`LuchoBazz/ielts-ai-dataset`](https://github.com/LuchoBazz/ielts-ai-dataset), giấy phép [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); đề do AI tạo, audio là giọng TTS. Đã chuyển định dạng sang SQL.
- **Audio:** Supabase Storage, bucket public `lori-audio`, thư mục `toeic-test-1/` và `ielts-test-1/`; URL đầy đủ ghi trong `exam_sections.audio_url`. App Android phát audio trực tiếp từ Supabase qua HTTPS, không đi qua Backend. Khi project Supabase bị tạm dừng (sau 1 tuần không hoạt động) audio không phát được cho đến khi khôi phục trong dashboard.
- **Quy trình nhập 1 đề:** (1) sinh SQL bằng `toeic_to_sql.py` hoặc `ielts_to_sql.py` (chạy trong Docker `python:3.12-slim`); (2) tải audio nguồn và upload lên bucket đúng thư mục/tên file mà SQL đã trỏ tới; (3) nạp SQL vào Neon bằng `psql` (Docker `postgres:16-alpine`, thông tin kết nối qua biến môi trường `PG*`, không ghi ra file); (4) chạy `tools/exam-import/verify_exam_data.sql`. Mỗi file SQL nằm trong 1 transaction nên lỗi thì không lưu gì. File SQL, audio và repo nguồn để ngoài repo.
- **Quy ước dữ liệu:**
  - `options` là mảng JSON theo thứ tự A, B, C… (không kèm tiền tố "A."; riêng TOEIC Part 1–2 không in nội dung lựa chọn nên là `["A","B","C","D"]` / `["A","B","C"]`); `correct_answer` của `MULTIPLE_CHOICE`/`MATCHING` là một chữ cái.
  - TOEIC: `order_index` câu hỏi là số câu TOEIC 1–200. Mỗi câu Part 1–2 là 1 section có audio riêng; mỗi hội thoại/bài nói Part 3–4 là 1 section; Part 5 là 1 section; mỗi đoạn Part 6–7 là 1 section có `passage_text`. Transcript và lời giải nằm ở `explanation`.
  - IELTS: Y/N/NG lưu dạng `MULTIPLE_CHOICE` (YES/NO/NOT GIVEN); hướng dẫn, bảng, đoạn tóm tắt và hộp từ của dạng điền từ nằm trong `question_text`.
- **Giới hạn đã biết:** chưa có ảnh cho câu TOEIC Part 1 và một số câu đồ họa Part 3–4 (V2 chưa có cột ảnh); một số câu điền từ IELTS có đáp án thay thế hợp lệ nhưng hệ thống chỉ chấp nhận đáp án chính.