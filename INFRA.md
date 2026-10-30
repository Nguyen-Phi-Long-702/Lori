# INFRA.md — Hạ tầng Backend Lori (Cloud Demo, chi phí $0)

> Tài liệu này ghi lại quy trình dựng hạ tầng Backend của Lori để cả team tham khảo/tái sử dụng.
> **Repo này là Public:** tài liệu chỉ ghi *tên* biến/secret, tuyệt đối không ghi giá trị, chuỗi kết nối, mật khẩu hay token.
> Hạn mức miễn phí bên dưới lấy từ tài liệu của từng nhà cung cấp, có thể thay đổi — kiểm tra lại khi cần.

## 1. Tổng quan

```
Android App
   ↓ HTTPS
Spring Boot Backend — Render (Web Service, Docker)
   ↓ TLS                       ↓ TLS          ↓ HTTPS + chữ ký HMAC
PostgreSQL — Neon           Redis — Upstash   AI Service (FastAPI) — Vercel

GitHub Actions (repo lori-db-backup, riêng tư) --pg_dump hàng tuần--> Neon
UptimeRobot --kiểm tra mỗi 5 phút--> Render
UptimeRobot --kiểm tra mỗi 5 phút--> AI Service (Vercel)
```

| Thành phần | Dịch vụ | Gói | Ghi chú giới hạn |
|---|---|---|---|
| Backend | Render Web Service | Free (không cần thẻ) | Tự ngủ sau 15 phút không có truy cập; 750 giờ chạy/tháng cho cả workspace |
| PostgreSQL | Neon | Free (không cần thẻ) | 100 CU-giờ/tháng, 0,5 GB/project, compute tự ngủ sau 5 phút, khôi phục theo thời điểm chỉ 6 giờ |
| Redis | Upstash | Free (không cần thẻ) | — |
| CI/CD + Backup | GitHub Actions | Free | — |
| Giám sát | UptimeRobot | Free | 50 monitor, kiểm tra mỗi 5 phút |
| Audio đề thi | Supabase Storage | Free (không cần thẻ) | 1 GB lưu trữ, 50 MB/file, 5 GB egress; project bị tạm dừng sau 1 tuần không hoạt động |
| AI Service | Vercel (Hobby) | Free (không cần thẻ) | 1.000.000 lần gọi hàm và 4 CPU-giờ hoạt động/tháng; chỉ dùng phi thương mại |
| LLM (suy luận) | Cloudflare Workers AI | Free (không cần thẻ) | 10.000 Neurons/ngày, reset 00:00 UTC; vượt hạn mức thì request lỗi chứ không tính tiền; model do Cloudflare chạy |

> Kế hoạch gốc dùng Google Cloud Run; vì team không dùng thẻ tín dụng nên Backend chạy trên **Render**.
> Kế hoạch gốc đặt AI Service trên VM Oracle Cloud Always Free (Docker Compose, DuckDNS, Caddy); vì team không dùng thẻ tín dụng nên AI Service chạy trên **Vercel Hobby**. Vercel tự cấp HTTPS và tên miền `*.vercel.app`.
> Kế hoạch gốc chạy LLM tự host (Ollama) trên VM Oracle; vì không có VM và không có nền tảng miễn phí nào không cần thẻ đủ RAM để tự host LLM, LLM chạy trên **Cloudflare Workers AI** (xem mục 17).

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
| `AI_SERVICE_BASE_URL` | URL gốc của AI Service trên Vercel, không có `/` ở cuối (xem mục 17) |
| `AI_SERVICE_SHARED_SECRET` | Khóa dùng chung để ký HMAC khi Backend gọi AI Service, ≥ 32 ký tự, cùng giá trị với biến trên Vercel (xem mục 17) |

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

### Monitor AI Service (Vercel)

- Monitor: `Lori AI Service (Vercel)`, loại **Keyword**, kiểm tra mỗi 5 phút, báo qua email (cùng người nhận với monitor Backend).
- URL theo dõi: `https://lori-ai-service.vercel.app/` (đường dẫn gốc `GET /`, công khai, không cần chữ ký).
- Keyword: `Lori AI Service`; cảnh báo khi keyword **không xuất hiện** trong nội dung trả về (`GET /` trả `{"message":"Hello from Lori AI Service"}`).
- **Vì sao dùng Keyword mà không dùng HTTP(s):** monitor HTTP(s) của UptimeRobot mặc định gửi `HEAD`, và chỉ gói Pro mới đổi được sang `GET`. Route `GET /` của FastAPI không tự nhận `HEAD` nên trả `405` → báo Down giả. Monitor Keyword mặc định gửi `GET` và dùng được ở gói Free.
- **Không theo dõi `POST /ai/ping`:** endpoint này cần chữ ký HMAC mà UptimeRobot không tạo được.
- Mỗi 5 phút là 1 lần gọi hàm của Vercel: khoảng 8.640 lần/tháng, chưa tới 1% hạn mức 1.000.000 lần gọi/tháng của gói Hobby.
- Nếu đổi nội dung trả về của `GET /` thì phải đổi keyword của monitor cho khớp, nếu không UptimeRobot sẽ báo Down.

## 8. Database migration (Flyway)

- Migration nằm ở `backend/src/main/resources/db/migration/`, đặt tên `V<số>__<mô_tả>.sql`.
- Flyway tự chạy khi Backend khởi động (cả local lẫn Render), dùng chung kết nối `NEON_URL`.
- `V1__init_schema.sql`: `users`, `refresh_tokens`, `user_progress`, `subscriptions`.
- `V2__exam_tables.sql`: `exam_papers`, `exam_sections`, `exam_questions`, `exam_results`.
- `V3__exam_section_image.sql`: thêm cột `exam_sections.image_url` (ảnh của section đề thi, cho phép NULL).
- `V4__exp_streak.sql`: thêm `users.total_exp` (tổng EXP) và bảng `user_study_days` (mỗi dòng = 1 ngày user đã học, tính theo giờ Việt Nam).
- `V5__ai_learning_tables.sql`: thêm 5 bảng cho AI Learning Context và Evaluation: `user_learning_profile`, `user_weakness_record`, `ai_feedback_metadata`, `ai_generated_exercise`, `ai_evaluation_result`. Kế hoạch ghi tên file là V3, nhưng V3 đã dùng cho ảnh section đề thi nên đổi thành V5. Backend chưa có entity cho các bảng này (làm ở Tuần 13).
- **Không sửa migration đã chạy** (Flyway kiểm tra checksum); muốn đổi schema thì tạo migration mới.


## 9. Đăng nhập Google (OAuth 2.0)

- Dịch vụ: Google Cloud Console → **Google Auth Platform**, dùng project riêng cho Lori. **Không bật thanh toán, không cần thẻ tín dụng** (tạo OAuth client không yêu cầu billing).
- **Audience:** External, trạng thái **Testing** → chỉ các tài khoản Google nằm trong danh sách **Test users** mới đăng nhập được. Chuyển sang In production là quyết định ở giai đoạn phát hành.
- **Web client:** Backend chỉ cần **Client ID** của Web client để kiểm tra trường `aud` của Google ID Token. **Client secret không dùng trong Backend**, không lưu vào repo hay biến môi trường Render.
- **Redirect URI** của Web client: `https://developers.google.com/oauthplayground` — chỉ để lấy ID Token thử bằng tay (OAuth 2.0 Playground).
- **Android client:** đã tạo ở Tuần 6 cho package `com.example.lori` với SHA-1 của **debug keystore** (mỗi máy build có SHA-1 riêng nên cần thêm client Android khác nếu build trên máy khác; bản release sẽ cần client riêng với SHA-1 của keystore release). Client này chỉ để Google nhận diện app, code không dùng Client ID của nó.
- **Phía app:** dùng **Credential Manager** (`GetSignInWithGoogleOption`) với **Web client ID** làm `serverClientId`, nên ID Token có `aud` = Web client ID, khớp `GOOGLE_CLIENT_ID` ở Backend. Web client ID đặt trong `local.properties` (khóa `GOOGLE_WEB_CLIENT_ID`, không commit) và được đọc vào `BuildConfig`.
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
- **Ảnh (TOEIC):** cột `exam_sections.image_url` (Flyway `V3__exam_section_image.sql`, NULL nếu section không có ảnh). TOEIC test 1 có 10 section có ảnh: 6 câu Part 1 (Photographs), 3 hội thoại đồ họa Part 3 (Conversation 11–13) và 1 bài nói đồ họa Part 4 (Talk 10). File ảnh để trong bucket `lori-audio`, thư mục `toeic-test-1/images/`; `image_url` được nạp bằng câu lệnh `UPDATE` sau khi nhập đề (script `toeic_to_sql.py` chưa sinh `image_url`). Backend trả `imageUrl` trong `GET /api/exams/{examId}`; app hiện ảnh bằng Glide. Màn Xem lại bài làm chưa hiện ảnh.
- **Giới hạn đã biết:** một số câu điền từ IELTS có đáp án thay thế hợp lệ nhưng hệ thống chỉ chấp nhận đáp án chính.


## 12. Dịch thuật (LibreTranslate) và Thanh toán mock

### LibreTranslate (dịch EN–VI)

- Backend gọi `POST /translate` của LibreTranslate từ `POST /api/translate` (tối đa 500 ký tự, hai chiều `en` ↔ `vi`, đăng nhập là dùng được, không cần Premium).
- **Hiện tại chạy tạm trên máy Dev** (kế hoạch v5, Tuần 5 Ngày 6): file `tools/libretranslate/docker-compose.yml`, chạy `docker compose up -d` trong thư mục đó, mở `http://localhost:5000`. Chỉ nạp 2 ngôn ngữ `en`, `vi` (`LT_LOAD_ONLY=en,vi`); lần chạy đầu phải tải model.
- Biến môi trường của Backend: `LIBRETRANSLATE_URL` (địa chỉ gốc, không có `/` ở cuối). Không đặt thì mặc định `http://localhost:5000`. Khi chạy Backend bằng Docker trên máy Dev, đặt `http://host.docker.internal:5000`.
- **Trên Render chưa có LibreTranslate** (Render không với tới máy Dev) → `POST /api/translate` trả `503 Translation service unavailable`; các API khác không bị ảnh hưởng. Kế hoạch dự định chạy LibreTranslate chung VM Oracle với AI Service từ Tuần 9; AI Service nay chạy trên Vercel, không có VM chung, nên chỗ chạy cố định vẫn chưa chốt.

### Thanh toán mock và hết hạn Premium

- `POST /api/payment/mock-purchase` (`{"planType":"MONTHLY|YEARLY|LIFETIME"}`) tạo bản ghi `subscriptions` (`payment_method = MOCK`) và bật Premium; không có giao dịch tiền thật. Google Play / VNPay / Momo là optional, chưa làm.
- Biến môi trường (đều tùy chọn): `PAYMENT_MOCK_ENABLED` (mặc định `true`; `false` thì mock-purchase trả 403), `SUBSCRIPTION_EXPIRY_CRON` (mặc định `0 0 * * * *` = đầu mỗi giờ).
- `SubscriptionExpiryScheduler` chạy theo lịch trên, đánh dấu gói `EXPIRED` và hạ `users.is_premium` của user hết hạn. Premium vĩnh viễn (`expires_at` rỗng) không bị hạ. Mỗi lần chạy sẽ đánh thức Neon (đang tự ngủ khi không có request).

## 13. Kiểm thử API (Postman Collection v2, load test k6)

### Postman Collection v2

- File: `postman/Lori-API-v2.postman_collection.json` (Collection v2.1). Bản v1 (`postman/Lori-Auth.postman_collection.json`) giữ lại để tham khảo.
- Gồm 3 thư mục:
  - `1. Auth flow`: register → login → truy cập API cần đăng nhập → refresh (xoay vòng) → logout, kèm các trường hợp 401.
  - `2. Premium flow`: register → login → user Free gọi `GET /api/exams` bị `403 PREMIUM_REQUIRED` → `POST /api/payment/mock-purchase` → gọi lại `GET /api/exams` thành công bằng cùng access token.
  - `3. AI Tutor flow` (Tuần 11 – Ngày 7): register → login → user Free gọi `POST /api/ai/chat` bị `403 PREMIUM_REQUIRED` → `mock-purchase` → hỏi tiếng Anh cơ bản → nạp tiến trình học bằng `POST /api/progress/sync` → hỏi AI về điểm yếu và hoạt động gần đây (kiểm tra Learning Context) → nộp 1 bài thi trắng → hỏi AI về kết quả thi (kiểm tra `recentExams` không được gửi cho tác vụ chat). Kết quả kiểm thử ở mục 20. Tuần 13 – Ngày 3-4 thêm T13-T18 kiểm thử `analyze-quiz` và `analyze-exam`, kết quả ở mục 24. Tuần 13 – Ngày 5-6 thêm T19-T23 kiểm thử `generate-exercise` và `recommend`, kết quả ở mục 25.
- Cần một Environment có 3 biến: `baseUrl` (URL Render, không có `/` ở cuối), `apiKey`, `appSignature`. **Không commit file Environment** (chứa khóa). Header `X-API-Key` và `X-App-Signature` được Pre-request script của collection thêm vào mọi request.
- Chạy: Postman → chuột phải thư mục (hoặc collection) → Run. Mỗi lần chạy đăng ký một email mới `postman.<thời gian>@example.com` nên để lại user thử trong Neon. Một lần chạy cả collection dùng 32 request (thư mục 3 gọi LLM thật 7 lần; ước tính khoảng 100 Neurons Cloudflare cho 4 lần đầu, chưa đo Neurons của 3 lần phân tích quiz/bài thi mới).
- Backend giới hạn 60 request/phút/IP: không chạy liên tục nhiều lần trong 1 phút.

### Load test (k6)

- Script: `tools/load-test/load-test.js`, chạy bằng Docker (image `grafana/k6`), không cần cài k6. Biến môi trường (chỉ ghi tên): `BASE_URL`, `API_KEY`, `APP_SIGNATURE`. Lệnh chạy (PowerShell, ở thư mục gốc repo, sau khi đặt 3 biến môi trường):
  `docker run --rm -e BASE_URL -e API_KEY -e APP_SIGNATURE -v "${PWD}\tools\load-test:/scripts" grafana/k6 run /scripts/load-test.js`
- Script tự đăng ký 1 user (`k6.<thời gian>@example.com`), nâng lên Premium bằng mock-purchase, rồi gọi 5 API CRUD: `GET /api/content/version`, `GET /api/users/me`, `GET /api/progress/pull`, `POST /api/progress/sync`, `GET /api/exams`.
- Vì Backend giới hạn 60 request/phút/IP (`RateLimitFilter`), script chỉ gửi ~50 request/phút trong 3 phút (~150 request). Đây là đo độ trễ ở mức tải thấp, **không phải** test nhiều người dùng đồng thời; test tải đồng thời (kế hoạch Tuần 17) phải tính tới giới hạn này.
- Ngưỡng (kế hoạch mục 20): avg < 300ms và p95 < 500ms cho từng API. Mỗi lần chạy để lại 1 user, 1 subscription `MOCK` và 1 dòng `user_progress` trong Neon (không tự xóa).
- Kết quả đo ngày 10/10/2026 (Render Free + Neon Free, chạy từ máy Dev; số đo gồm cả độ trễ mạng từ máy chạy test tới Render):

| API | avg | p(95) | Đạt ngưỡng |
|---|---|---|---|
| `GET /api/content/version` | 239.92ms | 407.81ms | Đạt |
| `GET /api/users/me` | 255.74ms | 466.84ms | Đạt |
| `GET /api/progress/pull` | 258.65ms | 530.25ms | Chưa đạt |
| `POST /api/progress/sync` | 313.74ms | 479.06ms | Chưa đạt |
| `GET /api/exams` | 293.78ms | 556.9ms | Chưa đạt |


## 14. Kiểm thử module đề thi (Tuần 7 – Ngày 7)

### Công cụ

- `tools/exam-import/verify_exam_data.sql`: kiểm tra toàn vẹn dữ liệu đề (mọi `bad_rows` phải bằng 0).
- `tools/exam-import/exam_test_expected.sql`: tính **kết quả kỳ vọng** cho các kịch bản test (chỉ đọc, không sửa dữ liệu). Cách chạy (PowerShell, ở thư mục gốc repo, sau khi đặt các biến môi trường `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`; chỉ ghi tên biến, không ghi giá trị):
  `docker run --rm -e PGHOST -e PGPORT -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGSSLMODE -v "${PWD}\tools\exam-import:/work" postgres:16-alpine psql -f /work/exam_test_expected.sql`
  (đổi tên file thành `verify_exam_data.sql` để chạy kiểm tra toàn vẹn).
- Kiểm tra link audio/ảnh: `HEAD` từng `audio_url`/`image_url` lấy từ DB (tất cả phải trả 200).

### Kết quả kỳ vọng (tính từ DB ngày 19/10/2026)

| Kịch bản | Cách làm | Kết quả kỳ vọng |
|---|---|---|
| B – TOEIC nộp bài trắng | Vào câu 200, nộp khi chưa trả lời câu nào | Listening 5 + Reading 5 = 10/990; 0/200 đúng |
| A – TOEIC chạy đủ 200 câu | Chọn A mọi câu, trừ câu 10, 50, 120, 180 để trống | Listening 130/495 (26 đúng), Reading 85/495 (16 đúng), tổng 215 (42 câu đúng) |
| C – TOEIC hết giờ | Tạm đổi `duration_minutes` thành 6, chỉ chọn A ở câu 1–5, đợi hết giờ rồi **khôi phục 120** | Tổng 15 (1 câu đúng: Listening 10, Reading 5) |
| IELTS – theo bảng hành động | Làm theo cột `hanh_dong` (mục 2 của `exam_test_expected.sql`) | Listening 21/40 → Band 5.5; Reading 18/40 → Band 5.0; tổng Band 5.5 |
| Premium gate | 6 endpoint `/api/exams/*` với user Free → 403 `PREMIUM_REQUIRED`; sau `mock-purchase` hết bị chặn | Đạt |

Bảng quy đổi band IELTS dùng để đối chiếu nằm trong `IeltsScoreUtil` (Listening/Reading, raw ≥ ngưỡng).

### Kết quả kiểm thử ngày 19/10/2026

| Hạng mục | Kết quả |
|---|---|
| Dữ liệu đề (toàn vẹn, 58 link audio, 10 link ảnh) | Đạt (sau khi sửa dữ liệu, xem bên dưới) |
| Premium gate (Backend + app) | Chưa kiểm thử |
| Danh sách đề + bộ lọc + Sắp ra mắt | Chưa kiểm thử |
| TOEIC: timer, audio, điều hướng, nộp bài, điểm, hết giờ tự nộp | Chưa kiểm thử |
| IELTS: Listening + Reading + điểm band | Chưa kiểm thử |
| `GET /api/exams/{id}` (TOEIC 200 câu) | Chưa đo |

### Lỗi dữ liệu đã sửa trong ngày

- `exam_sections.image_url` còn trống vì script nhập đề không nạp cột này; đã nạp bằng `UPDATE` cho 10 section (6 Part 1, 3 hội thoại Part 3, 1 bài nói Part 4).
- 9 trong 10 file ảnh trên Supabase có đuôi `.jpg` (nội dung thực là PNG), riêng `4294338.png` là `.png`; đã sửa `image_url` cho khớp. Sửa dữ liệu trong DB nên không có commit code.

### Giới hạn đã biết (không sửa)

- Câu điền từ IELTS chỉ chấp nhận đáp án chính (chưa hỗ trợ đáp án thay thế).
- Màn Xem lại chưa hiện ảnh và đoạn văn của đề.
- Điểm TOEIC quy đổi bằng công thức tuyến tính gần đúng (ETS không công bố bảng quy đổi chính thức).
- Section Listening Part 4 có `passage_text` chứa dòng hướng dẫn *"refer to the following …"* (Talk 9–10 còn có biên nhận/danh sách cần cho câu hỏi); đây là dữ liệu đúng thiết kế, không phải transcript.


## 15. Hệ thống EXP và Streak (Tuần 8)

- Toàn bộ việc tính EXP và streak nằm ở Backend (`ExpService`, `StreakService`); không tin con số EXP do app gửi. Tổng EXP lưu ở `users.total_exp` (chỉ cộng bằng `UPDATE ... SET total_exp = total_exp + n`). Streak không lưu sẵn mà tính từ bảng `user_study_days`. Mọi lần cộng EXP đều đi qua `ExpService.award()`.
- Nguồn tính EXP: `POST /api/progress/sync` (mỗi mục tiến trình vừa được ghi) và `POST /api/exams/{id}/submit`.

| Sự kiện | EXP |
|---|---|
| Hoàn thành bài ngữ pháp (`grammar_lesson`) | +15 |
| Hoàn thành quiz từ vựng (`vocab_quiz_topic`) | 10 + 20 × tỉ lệ đúng (10–30) |
| Hoàn thành quiz ngữ pháp (`grammar_quiz_lesson`) | 15 + 25 × tỉ lệ đúng (15–40) |
| Nộp bài thi | 50 + 150 × tỉ lệ đúng (50–200) |
| Ngày học mới | +20 |

- Cấp độ: cấp *n* cần 50·n·(n−1) EXP tích lũy (cấp 2 = 100, cấp 5 = 1.000, cấp 10 = 4.500).
- Ngày học và "hôm nay" tính theo giờ Việt Nam (`Asia/Ho_Chi_Minh`). Chỉ nhận ngày học từ 7 ngày trước đến hôm nay; dữ liệu ngoài khoảng đó vẫn được đồng bộ nhưng không tính EXP/streak.
- Chống cộng thừa: mỗi mục tiến trình tối đa 1 lần được tính EXP mỗi ngày; mỗi đề thi tối đa 1 lần mỗi ngày; gửi lại dữ liệu cũ không bị cộng đôi.
- `GET /api/users/me/stats` trả thêm `totalExp`, `level`, `currentStreak`, `longestStreak`.

### Giới hạn đã biết (không sửa)

- Chưa có EXP "học từ vựng +5" của kế hoạch: flashcard chưa ghi tiến độ vào `user_progress_local` nên server không có sự kiện để cộng.
- Tiến trình đã đồng bộ trước khi có tính năng này không được cộng EXP hồi tố.
- EXP quiz dựa trên số câu đúng/sai app gửi lên (app bị sửa có thể gửi số đẹp); biện pháp chống gian lận chỉ ở mức giới hạn ngày và 1 lần/mục/ngày.
- Ngày học lấy từ `lastStudiedAt` do thiết bị gửi, nên thiết bị lệch giờ quá 7 ngày hoặc ở tương lai (kể cả giả lập có đồng hồ sai) sẽ không được tính EXP/streak.
- Màn Profile (Android) vẫn tự tính streak trên máy từ `user_progress_local`, chưa đọc từ server.


## 16. Bảng xếp hạng (Tuần 8)

- Dữ liệu bảng xếp hạng nằm ở Redis (Upstash), kiểu Sorted Set, 3 key: `leaderboard:global`, `leaderboard:weekly`, `leaderboard:monthly`. Member = `userId`, score = EXP.
- `global` = tổng EXP (khớp `users.total_exp`); `weekly` / `monthly` = EXP kiếm được từ đầu tuần / đầu tháng (giờ Việt Nam).
- Mọi lần cộng EXP (`ExpService.award()`) gọi `LeaderboardService.updateScore()` để cộng điểm vào cả 3 bảng, **sau khi** transaction DB commit. Redis lỗi chỉ ghi log, không làm hỏng request của user.
- Mỗi lần Backend khởi động, `leaderboard:global` được nạp lại từ `users.total_exp` (user có EXP từ trước khi có bảng xếp hạng, hoặc Redis mất dữ liệu, vẫn có mặt trong bảng). Dòng log: `Nap leaderboard:global tu DB: N user`.
- Reset: `LeaderboardResetScheduler` xóa `leaderboard:weekly` lúc 00:00 thứ Hai và `leaderboard:monthly` lúc 00:00 ngày 1 hằng tháng (giờ Việt Nam). Bảng `global` không bao giờ bị reset. Biến môi trường tùy chọn: `LEADERBOARD_WEEKLY_RESET_CRON` (mặc định `0 0 0 * * MON`), `LEADERBOARD_MONTHLY_RESET_CRON` (mặc định `0 0 0 1 * *`); cron 6 trường của Spring.
- Bảng xếp hạng không lưu ở PostgreSQL nên không cần backup riêng; backup Neon hàng tuần ở mục 6 vẫn giữ `users.total_exp`.
- Free hay Premium đều xem được; yêu cầu đăng nhập và các header `X-API-Key`, `X-App-Signature` như mọi API khác.

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/leaderboard/{period}?page=0&size=20` | Một trang bảng xếp hạng, điểm cao nhất trước. `period` = `global` / `weekly` / `monthly`; `page` từ 0; `size` 1–50. Mỗi dòng: `rank`, `displayName`, `avatarUrl`, `score` |
| GET | `/api/leaderboard/{period}/me` | Hạng và điểm của chính user (`rank` = `null`, `score` = 0 nếu chưa có điểm trong bảng đó) |

- `period` sai, `page` < 0, `size` ngoài 1–50 → `400`.
- Mỗi lần cộng EXP tốn 3 lệnh Redis; xem một trang bảng tốn 1 lệnh, xem "vị trí của tôi" tốn 2 lệnh.

### Giới hạn đã biết (không sửa)

- Reset tuần/tháng chạy bằng `@Scheduled` trong Backend: nếu Backend không chạy đúng lúc 00:00 (đang deploy lại, hoặc Render ngủ), lần reset đó bị bỏ qua và bảng tuần/tháng giữ điểm cũ đến lần reset kế tiếp. UptimeRobot (mục 7) giữ Backend luôn thức nên trường hợp này hiếm.
- Hai user bằng điểm được Redis xếp theo thứ tự chữ của `userId`, không theo ai đạt điểm trước.
- Bảng tuần/tháng không dựng lại được nếu Redis mất dữ liệu (DB không lưu lịch sử EXP theo ngày); chỉ bảng `global` được nạp lại khi khởi động.
- EXP kiếm được trước khi có bảng xếp hạng chỉ có mặt trong bảng `global`, không có trong bảng tuần/tháng.
- Nếu Redis lỗi đúng lúc cộng EXP, DB vẫn có EXP nhưng bảng xếp hạng bị thiếu điểm đó cho đến khi Backend khởi động lại (bảng `global` được nạp lại; bảng tuần/tháng không).

## 17. AI Service (Vercel) — Tuần 9

- Dịch vụ: **Vercel Hobby** (không cần thẻ tín dụng). Mã nguồn ở thư mục `ai-service/` (FastAPI); Root Directory của project Vercel = `ai-service`; URL production: **https://lori-ai-service.vercel.app**.
- Kế hoạch gốc chạy AI Service trên VM Oracle Cloud Always Free (Docker Compose, DuckDNS, Caddy); vì team không dùng thẻ tín dụng nên chuyển sang Vercel. Không có VM hay container tự quản lý nên **không có** `restart: always` và không có bước kiểm tra khởi động lại VM; nền tảng tự lo việc chạy hàm.
- Biến môi trường (chỉ ghi tên, không ghi giá trị):

| Nơi đặt | Tên biến | Ý nghĩa |
|---|---|---|
| Vercel (project AI Service) | `AI_SERVICE_SHARED_SECRET` | Khóa dùng chung để ký/kiểm tra HMAC, ≥ 32 ký tự |
| Render (Backend) | `AI_SERVICE_SHARED_SECRET` | Cùng giá trị với biến trên Vercel |
| Render (Backend) | `AI_SERVICE_BASE_URL` | URL gốc của AI Service, không có `/` ở cuối (không đặt thì mặc định `http://localhost:8000`) |
| Vercel (project AI Service) | `CLOUDFLARE_ACCOUNT_ID` | Account ID Cloudflare, dùng để gọi Workers AI |
| Vercel (project AI Service) | `CLOUDFLARE_API_TOKEN` | API token Workers AI (mục "LLM: Cloudflare Workers AI"); không commit |
| Vercel (project AI Service) | `AI_PROVIDER` | Tùy chọn: `self_hosted` (mặc định), `mock` hoặc `external`; không đặt thì dùng `self_hosted`; giá trị khác làm AI Service lỗi khi khởi động |

| Method | Đường dẫn | Xác thực | Mô tả |
|---|---|---|---|
| GET | `/` | Không | Trả `{"message":"Hello from Lori AI Service"}`; UptimeRobot theo dõi đường dẫn này (mục 7) |
| POST | `/ai/ping` | Chữ ký HMAC | Trả `{"status":"ok"}`; Backend gọi qua `AiServiceClient.ping()` |
| GET | `/ai/health` | Chữ ký HMAC | Trả `{"status":"ok","provider":"self_hosted"}`; `status` là `degraded` khi provider thiếu cấu hình; chỉ kiểm tra cấu hình, không gọi Cloudflare |
| POST | `/ai/chat` | Chữ ký HMAC | Body `{"message":"...","learningContext":{...}}` (`learningContext` có thể thiếu hoặc `null`); trả `{"type":"chat","answer":"...","truncated":false}`; `400` khi câu hỏi rỗng sau khi làm sạch, `422` khi thiếu `message`, `502` khi provider lỗi (thông điệp chung, không kèm lỗi của Cloudflare) |
| POST | `/ai/grammar` | Chữ ký HMAC | Body `{"text":"...","lessons":{"1":"Present Simple",...},"learningContext":{...}}` (`learningContext` có thể thiếu hoặc `null`); trả `{"type":"grammar_analysis","correct":false,"correctedSentence":"...","errors":[{"type":"...","explanation":"..."}],"lessonId":1}`; `400` khi câu rỗng sau khi làm sạch, `422` khi thiếu `text` hoặc `lessons`, `502` khi provider lỗi hoặc trả JSON không dùng được |
| POST | `/ai/vocabulary` | Chữ ký HMAC | Body `{"text":"affect, effect","learningContext":{...}}` (`learningContext` có thể thiếu hoặc `null`); trả `{"type":"vocabulary_analysis","words":[...],"difference":"..."}` (chi tiết ở mục 22); `400` khi input không hợp lệ (rỗng sau khi làm sạch, quá 2 từ/cụm từ, mỗi từ/cụm từ quá 40 ký tự), `422` khi thiếu `text`, `502` khi provider lỗi hoặc trả JSON không dùng được |

### Provider và Orchestrator

- `AiProvider` (`ai-service/providers/`): `SelfHostedLlmProvider` gọi model chính trong `config/model_config.py` qua Cloudflare Workers AI; `MockProvider` dùng cho test/CI; `ExternalApiProvider` là chỗ trống, không dùng cho MVP. Biến `AI_PROVIDER` chọn provider khi AI Service khởi động.
- `AiOrchestrator` (`ai-service/orchestrator/`): nhận `task_type`, chạy workflow tương ứng. Workflow hiện có: `chat` (`orchestrator/chat_workflow.py`): `sanitize_input` → `build_prompt` → `provider.generate` → `{"type","answer","truncated"}`; câu trả lời rỗng của model bị coi là lỗi provider. Workflow được nối trong `main.py`; endpoint `/ai/chat` ở `routers/chat.py`.
- Workflow `grammar` (`orchestrator/grammar_workflow.py`, `TaskType.GRAMMAR`): `sanitize_input` → `build_grammar_prompt` (chỉ dẫn + `<lesson_list>` + Learning Context + câu) → `provider.generate` → lấy JSON từ câu trả lời → kiểm tra → `{"type":"grammar_analysis","correct","correctedSentence","errors","lessonId"}`. Thiếu trường, sai kiểu hoặc câu sai mà không có lỗi nào bị coi là lỗi provider; `lessonId` không có trong `lessons` bị đổi thành `null`. Endpoint `/ai/grammar` ở `routers/grammar.py`.
- Lỗi từ provider là `ProviderError`; thông điệp có thể chứa nội dung lỗi của Cloudflare nên không trả nguyên văn cho client.

### Context Builder và Input Sanitizer (Tuần 11)

- `ai-service/guardrails/ai_input_sanitizer.py`: `sanitize_input(text)` làm sạch câu hỏi của học viên trước khi đưa vào prompt (Guardrails lớp 3). Thứ tự: cắt thô 4.000 ký tự → giải mã HTML entity → chuẩn hóa NFKC → bỏ ký tự bất thường (điều khiển, vô hình/zero-width, đảo chiều chữ, private use, ngắt dòng Unicode) → bỏ khối code (ba dấu backtick), thẻ HTML (kể cả nội dung `<script>`/`<style>`) → bỏ `<`, `>` và dấu backtick còn sót → gom khoảng trắng → cắt còn tối đa `MAX_INPUT_LENGTH` = 1.000 ký tự. Kết quả rỗng → `InvalidInputError`.
- Quá dài thì **cắt**, không từ chối (kế hoạch mục 4.2).
- `ai-service/context/context_builder.py`: `build_prompt(message, learning_context)` ghép Learning Context (JSON camelCase do Backend gửi, mục 18) và câu đã làm sạch thành prompt: khối `<learner_context>` (chỉ có khi ít nhất một phần có dữ liệu) rồi khối `<learner_message>`. Không đưa `submittedAt` của kỳ thi vào prompt.
- Thứ tự bắt buộc: `sanitize_input` trước, `build_prompt` sau. Sanitizer xóa sạch mọi ký tự `<` và `>` nên học viên không giả được thẻ ranh giới của prompt.
- Hai hàm được `chat_workflow` gọi đúng thứ tự trên.
- Giới hạn đã biết: chưa chặn pattern SQL/path traversal và chưa có Topic classifier (Tuần 15); khối code học viên dán vào bị bỏ hoàn toàn; chỉ `<` + chữ cái, `/`, `!` được coi là thẻ HTML.

### Xác thực Server-to-Server

- Backend ký mỗi request gửi sang AI Service bằng HMAC-SHA256, khóa là `AI_SERVICE_SHARED_SECRET`. Chuỗi được ký: `timestamp \n METHOD \n path \n sha256-hex(body)`. Chữ ký (hex) nằm ở header `X-Lori-Signature`, thời điểm (giây epoch) ở `X-Lori-Timestamp`.
- AI Service (`ai-service/guardrails/s2s_auth.py`) tính lại chữ ký, so sánh bằng `hmac.compare_digest` và chỉ nhận request lệch thời gian không quá 300 giây. Thiếu header, sai chữ ký, timestamp sai định dạng hoặc hết hạn → `401 Unauthorized`.
- Fail closed: nếu `AI_SERVICE_SHARED_SECRET` chưa đặt hoặc ngắn hơn 32 ký tự, AI Service từ chối mọi request cần chữ ký (401). Phía Backend, `AiServiceClient` trả `503 AI service unavailable` khi chưa có secret hoặc khi AI Service lỗi/từ chối.
- Công thức ký được khóa bằng một test vector chung: `AiServiceSignerTest` (Backend) và `test_s2s_auth.py` (AI Service) phải cho cùng một chữ ký.

### Tài nguyên thực tế của AI Service (Vercel Hobby)

> Kế hoạch gốc yêu cầu đo RAM/CPU của VM Oracle bằng lệnh hệ thống. Không có VM nên không có số đo đó; bảng dưới là giới hạn của hàm Vercel Hobby, lấy từ tài liệu chính thức (https://vercel.com/docs/functions/limitations) ngày 22/10/2026.

| Hạng mục | Giá trị (gói Hobby) |
|---|---|
| Bộ nhớ / CPU mỗi hàm | 2 GB / 1 vCPU |
| Thời gian chạy tối đa mỗi lần gọi | 300 giây |
| Dung lượng gói triển khai (chưa nén), runtime Python | 500 MB |
| Kích thước tối đa của request/response body | 4,5 MB |
| Vùng chạy hàm (theo Settings của project) | sin1 |

- Hàm chỉ chạy khi có request, không có tiến trình nền chạy liên tục. Container image trên Vercel cũng chạy như hàm và dùng cùng các giới hạn trên.
- Hạn mức hàng tháng (số lần gọi, CPU-giờ, GB-giờ) xem ở mục "Giới hạn đã biết" bên dưới.

### LLM: Cloudflare Workers AI

- Kế hoạch gốc cài Ollama trên VM Oracle ở Tuần 9. Vì AI Service chạy trên Vercel nên **không cài Ollama**: Ollama là dịch vụ chạy nền liên tục, còn hàm Vercel chỉ chạy theo request với các giới hạn ở bảng trên.
- Không có nền tảng miễn phí nào không cần thẻ đủ RAM để tự host LLM (Vercel Hobby tối đa 2 GB RAM; Render/Railway Free dưới 1 GB; Hugging Face Spaces yêu cầu gói PRO để tạo Docker Space; Oracle yêu cầu thẻ tín dụng thật). LLM vì vậy chạy trên **Cloudflare Workers AI**: model open-weight do Cloudflare chạy, gọi qua HTTPS API.
- Gói **Workers Free**, không cần thẻ, tài khoản không gắn phương thức thanh toán. Hạn mức **10.000 Neurons/ngày**, reset lúc 00:00 UTC (07:00 giờ Việt Nam). Vượt hạn mức thì request lỗi, **không bị tính tiền**; chỉ gói Workers Paid mới tính phí. Không nâng cấp gói. Nguồn: https://developers.cloudflare.com/workers-ai/platform/pricing/ (kiểm tra ngày 22/10/2026).
- Cần 2 giá trị để gọi API: **Account ID** và **API token** (tạo từ template "Workers AI" ở Workers AI → Use REST API). Chỉ lưu ở trình quản lý mật khẩu; **không commit**. Khi chạy thử bằng PowerShell dùng tên biến `CLOUDFLARE_ACCOUNT_ID` và `CLOUDFLARE_API_TOKEN`.
- Endpoint: `https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/ai/v1/chat/completions` (tương thích OpenAI) hoặc `…/ai/run/<MODEL_ID>`; header `Authorization: Bearer <API_TOKEN>`.
- Danh sách model ứng viên: `ai-service/BENCHMARK.md`.
- Khác kế hoạch gốc: vì model do Cloudflare chạy nên không đo được RAM, CPU, quantization, thời gian nạp model.

### Giới hạn đã biết (không sửa)

- Vercel Hobby: mỗi tháng 1.000.000 lần gọi hàm, 4 CPU-giờ hoạt động, 360 GB-giờ bộ nhớ; chỉ dùng cho mục đích phi thương mại. Vượt hạn mức thì tính năng bị tạm dừng, thường phải chờ 30 ngày.

## 18. Learning Context (Tuần 11)

- Learning Context là dữ liệu học tập súc tích mà Backend gửi cho AI Service; AI Service không đọc database. `LearningContextService.build(userId, taskType)` tổng hợp từ `user_progress` và `exam_results`, chỉ lấy các phần mà `AiContextPolicy` cho phép tác vụ đó (phần không được phép thì không truy vấn DB). Kết quả không chứa id, email hay token của user.
- Phần context theo tác vụ:

| `AiTaskType` | Điểm yếu ngữ pháp | Điểm yếu từ vựng | Hoạt động gần đây | Kỳ thi gần đây |
|---|:-:|:-:|:-:|:-:|
| `CHAT`, `RECOMMEND` | ✅ | ✅ | ✅ | |
| `GRAMMAR` | ✅ | | | |
| `VOCABULARY` | | ✅ | | |
| `GENERATE_EXERCISE`, `ANALYZE_QUIZ`, `CONVERSATION` | ✅ | ✅ | | |
| `ANALYZE_EXAM` | ✅ | ✅ | | ✅ |
| `IDENTIFY_IMAGE` | | | | |

- Quy tắc: "yếu" = quiz có tỉ lệ đúng dưới 60%; tối đa 5 mục yếu mỗi loại, 10 mục hoạt động gần đây, 3 lần thi gần nhất (hằng số ở đầu `LearningContextService`).
- Tên bài ngữ pháp / chủ đề từ vựng nằm ở `backend/src/main/resources/ai/learning-names.json`, sinh từ `dictionary.db` bằng `tools/ai-context/export_names.py` (PowerShell, ở thư mục gốc repo): `docker run --rm -v "${PWD}:/work" -w /work python:3.12-slim python tools/ai-context/export_names.py`. Chạy lại và commit file JSON mỗi khi thêm hoặc đổi bài ngữ pháp / chủ đề trong `dictionary.db`. Id không có trong file được ghi là `Grammar lesson <id>` / `Vocabulary topic <id>`.
- `AiGatewayService.chat` gọi `LearningContextService.build(userId, CHAT)` (mục 19); 5 bảng của V5 chưa được ghi dữ liệu.

### Giới hạn đã biết (không sửa)

- `user_progress` chỉ lưu tổng đúng/sai của lần làm gần nhất cho mỗi bài/chủ đề, không lưu từng câu hay từng từ, nên context không có thông tin kiểu "từ X sai 5 lần".
- Context chưa có level ước tính và mục tiêu học (làm ở Tuần 13).
- Tên bài/chủ đề lấy từ file tĩnh, không tự cập nhật khi `dictionary.db` đổi.

## 19. AI Tutor chat (Tuần 11)

- Endpoint: `POST /api/ai/chat` (`AiGatewayController`), yêu cầu đăng nhập và Premium (`@RequirePremium`, user Free nhận `403 PREMIUM_REQUIRED`).
- Request: `{"message": "..."}`; `message` không rỗng, tối đa 4.000 ký tự. Response `200`: `{"type":"chat","answer":"...","truncated":false}`; `truncated = true` khi câu trả lời bị cắt do hết `max_output_tokens`.
- Luồng: JWT → `@RequirePremium` → Bean Validation → `AiRateLimiter` → `LearningContextService.build(userId, CHAT)` → `AiServiceClient.chat` (ký HMAC, mục 17) → AI Service `POST /ai/chat` → Cloudflare Workers AI. Backend chỉ gửi sang AI Service câu hỏi và Learning Context, không gửi `userId`.
- Rate limit: 20 request/phút/user (Bucket4j trên Upstash, key `ai_rate_limit:chat:<userId>`); vượt thì `429 Too many requests`. Giới hạn 60 request/phút/IP của `RateLimitFilter` vẫn áp dụng song song.
- Mã lỗi: `400` (`message` trống, vượt 4.000 ký tự, hoặc rỗng sau khi làm sạch), `401` (chưa đăng nhập), `403` (Free hoặc sai API key), `429` (quá lượt), `503 AI service unavailable` (AI Service hoặc Cloudflare lỗi, hết hạn mức, thiếu secret, quá 30 giây).
- Không thêm biến môi trường mới so với mục 17.

### Giới hạn đã biết (không sửa)

- Mỗi request độc lập: AI không nhớ các câu hỏi trước đó trong cùng cuộc hội thoại.
- Hạn mức Cloudflare 10.000 Neurons/ngày dùng chung cho mọi user: model chính tốn khoảng 19-24 Neurons/câu hỏi (`ai-service/BENCHMARK.md` mục 5, 7), tức khoảng 400-500 câu/ngày; hết hạn mức thì chat trả 503 đến 00:00 UTC (07:00 giờ Việt Nam). Không tự chuyển sang model dự phòng.
- Chưa có Topic classifier, kiểm tra output và log AI đã che thông tin cá nhân (Tuần 15); `AiOutputValidator` chưa có nên câu trả lời của model được trả nguyên văn.
- Timeout: AI Service chờ Cloudflare tối đa 25 giây, Backend chờ AI Service tối đa 30 giây.

## 20. Kiểm thử AI Tutor (Tuần 11 – Ngày 7)

### Công cụ

- Postman Collection v2, thư mục `3. AI Tutor flow` (mục 13): 12 request chạy tuần tự, đi qua đường thật Postman → Render (Backend) → Vercel (AI Service) → Cloudflare Workers AI, không dùng mock. Cần Environment `baseUrl`, `apiKey`, `appSignature` như mục 13.
- Không có code kiểm thử mới ở Backend và AI Service. Unit test `LearningContextService` và `AiContextPolicy` làm ở Tuần 16 theo kế hoạch.

### Dữ liệu kiểm thử

- Mỗi lần chạy tạo 1 user mới (Free), mua Premium bằng `mock-purchase`, rồi nạp 3 mục học bằng `POST /api/progress/sync` với ngày học cách đây 30 ngày (nằm ngoài khoảng 7 ngày của `ExpService` nên không cộng EXP):

| `itemType` | `itemId` | Tên | Đúng / Sai |
|---|---|---|---|
| `grammar_quiz_lesson` | 26 | Subjunctive Mood | 2 / 8 |
| `vocab_quiz_topic` | 13 | Giáng sinh | 3 / 7 |
| `grammar_lesson` | 27 | Inversion | 0 / 0 |

- Rồi nộp 1 bài thi trắng (đề đầu tiên trong `GET /api/exams`, không có đáp án nào) để user có 1 kết quả thi.

### Kịch bản

| # | Kịch bản | Request | Kết quả kỳ vọng | Kết quả ngày 25/10/2026 |
|---|---|---|---|---|
| 1 | Premium gate | T3: user Free `POST /api/ai/chat` | `403`, `message` = `PREMIUM_REQUIRED` | Đạt |
| 2 | Hỏi tiếng Anh cơ bản | T5: sự khác nhau giữa *a* và *an* | `200`, `type` = `chat`, `truncated` = `false`, câu trả lời đúng ý (nguyên âm) | Đạt |
| 3 | Learning Context: điểm yếu ngữ pháp và từ vựng | T7: hỏi chủ đề yếu nhất | AI nêu `Subjunctive Mood` và `Giáng sinh` | Đạt |
| 4 | Learning Context: hoạt động gần đây | T8: hỏi các bài ngữ pháp đã học | AI nêu `Inversion` | Đạt |
| 5 | Policy: tác vụ chat không nhận kỳ thi gần đây | T12: hỏi điểm bài thi vừa nộp | AI không biết kết quả thi (không nêu điểm hay tên đề) | Đạt |

### Dấu vết để lại sau mỗi lần chạy

- 1 user thử, 1 subscription `MOCK`, 3 dòng `user_progress`, 1 kết quả thi trong Neon (không tự xóa).
- Bài thi trắng cộng khoảng 70 EXP (50 EXP nộp bài + 20 EXP ngày học mới) nên user "Postman Tester" xuất hiện trên bảng xếp hạng.
- Khoảng 100 Neurons Cloudflare mỗi lần chạy (hạn mức 10.000 Neurons/ngày).

### Giới hạn đã biết (không sửa)

- Không xem được prompt thật gửi cho model (Vercel và Cloudflare không ghi nội dung prompt ra log), nên Learning Context chỉ được kiểm tra gián tiếp qua câu trả lời của LLM. Kiểm tra chính xác từng trường theo `AiContextPolicy` bằng unit test ở Tuần 16.
- Kịch bản 3, 4, 5 dựa trên hành vi của LLM: dù `temperature` = 0, câu trả lời vẫn có thể đổi nếu Cloudflare cập nhật model. Test tự động chỉ kiểm tra từ khóa; cần người đọc câu trả lời để kết luận.

## 21. Phân tích ngữ pháp AI (Tuần 12 – Ngày 1-2)

- Endpoint: `POST /api/ai/analyze-text` (`AiGatewayController`), yêu cầu đăng nhập và Premium (`@RequirePremium`). Request: `{"type":"grammar","text":"..."}`; `type` nhận `grammar` (mục này) hoặc `vocabulary` (mục 22); `text` không rỗng
- Response `200`: `{"type":"grammar_analysis","correct":false,"correctedSentence":"She doesn't like coffee.","errors":[{"type":"Subject-verb agreement","explanation":"..."}],"lessonId":1}`. Câu đúng: `correct = true`, `errors = []`, `lessonId = null`. `lessonId` là id bài trong `grammar_lessons` của `dictionary.db` (Android mở bài bằng `GrammarDao.getLessonById`).
- Luồng: JWT → `@RequirePremium` → Bean Validation → `AiRateLimiter.checkAnalyzeText` → `LearningContextService.build(userId, GRAMMAR)` (chỉ điểm yếu ngữ pháp, mục 18) → `AiServiceClient.analyzeGrammar` (ký HMAC, mục 17) → AI Service `POST /ai/grammar` → Cloudflare Workers AI. Backend gửi sang AI Service câu cần phân tích, danh sách 30 bài ngữ pháp (`learning-names.json`) và Learning Context, không gửi `userId`.
- Liên kết bài ngữ pháp: model chỉ được chọn `lessonId` trong danh sách Backend gửi; AI Service đổi id không có trong danh sách thành `null`.
- Rate limit: 20 request/phút/user, bucket riêng `ai_rate_limit:analyze-text:<userId>` (không dùng chung với chat). Giới hạn 60 request/phút/IP của `RateLimitFilter` vẫn áp dụng song song.
- Mã lỗi: `400` (`type` khác `grammar`/`vocabulary`, `text` trống, vượt 4.000 ký tự, hoặc rỗng sau khi làm sạch), `401`, `403` (Free hoặc sai API key), `429`, `503 AI service unavailable` (AI Service/Cloudflare lỗi, model trả JSON không dùng được, hết hạn mức, quá 30 giây).
- Không thêm biến môi trường hay thư viện mới so với mục 17.

### Giới hạn đã biết (không sửa)

- Chưa có ví dụ câu và bài tập liên quan (Module 5.7, Tuần 13); chưa ghi `ai_feedback_metadata`.
- Giải thích bằng tiếng Anh đơn giản (theo `SYSTEM_PROMPT`); chưa có Topic classifier, kiểm tra output và log đã che thông tin cá nhân (Tuần 15).
- Mỗi request chỉ kiểm tra tối đa 1.000 ký tự đầu của câu sau khi làm sạch; model có thể sửa khác nhau giữa các lần gọi nếu Cloudflare đổi model.
- Model có thể chọn `lessonId` chưa thật sát lỗi; đã kiểm tra thủ công ở Tuần 12 Ngày 7 (mục 23).

## 22. Vocabulary Tutor (Tuần 12 – Ngày 3-4)

- Endpoint: `POST /api/ai/analyze-text` với `type = vocabulary` (cùng endpoint với mục 21, yêu cầu đăng nhập và Premium). Request: `{"type":"vocabulary","text":"affect, effect"}`. `text` là 1 hoặc 2 từ/cụm từ, ngăn cách bằng dấu phẩy, dấu `/` hoặc `vs`; sau khi làm sạch được đổi sang chữ thường, hai từ trùng nhau tính là một; mỗi từ/cụm từ tối đa 40 ký tự.
- Response `200`: `{"type":"vocabulary_analysis","words":[{"word":"affect","inDictionary":true,"ipa":"/ˈæ.ˌfɛkt/","definitions":[{"pos":"V","meaning":"Làm ảnh hưởng đến, ...","example":"..."}],"explanation":"...","synonyms":[],"antonyms":[],"collocations":["affect health"]}],"difference":null}`. `words` theo đúng thứ tự user nhập; `difference` là phần phân biệt hai từ, `null` khi chỉ hỏi 1 từ.
- Nguồn chuẩn là từ điển Lori: AI Service đọc `ai-service/data/dictionary.db` (bản sao của `app/src/main/assets/databases/dictionary.db`, mở ở chế độ chỉ đọc, bằng `sqlite3` có sẵn của Python) bằng `ai-service/context/dictionary_lookup.py`. Đây là dữ liệu từ điển công khai, không phải dữ liệu của user nên không trái nguyên tắc "AI Service không đọc database" ở mục 18.
- Cách ghép kết quả (`ai-service/orchestrator/vocabulary_workflow.py`): từ có trong từ điển (`inDictionary = true`) thì `ipa` (phát âm đầu tiên), `definitions` (tối đa 5 nghĩa đầu, kèm mã từ loại `pos` và ví dụ nếu có) lấy từ từ điển; `synonyms`/`antonyms` ưu tiên của từ điển (tối đa 8), từ điển không có thì lấy của model (tối đa 5); `explanation`, `collocations` (tối đa 5) và `difference` luôn do model sinh. Từ không có trong từ điển thì `inDictionary = false`, `ipa = null`, `definitions = []` và nghĩa chỉ có ở `explanation` của model. Prompt đưa nghĩa từ điển cho model làm chuẩn và dặn không được nói trái.
- Luồng: JWT → `@RequirePremium` → Bean Validation → `AiRateLimiter.checkAnalyzeText` (chung bucket 20 request/phút/user với grammar) → `LearningContextService.build(userId, VOCABULARY)` (chỉ điểm yếu từ vựng, mục 18) → `AiServiceClient.analyzeVocabulary` (ký HMAC, mục 17) → AI Service `POST /ai/vocabulary` → `sanitize_input` → tách từ → tra từ điển → Cloudflare Workers AI → ghép kết quả. Backend chỉ gửi `text` và Learning Context, không gửi `userId`.
- Mã lỗi: như mục 21. Khác biệt: Backend trả `400` với thông điệp `Invalid input` cho mọi lỗi `400` từ AI Service (trước đây là `Message is empty after cleaning`, vì giờ còn lỗi sai số từ/độ dài từ).
- Không thêm biến môi trường hay thư viện mới so với mục 17. Dung lượng `dictionary.db` (khoảng 37 MB) nằm trong giới hạn gói triển khai 500 MB của Vercel Hobby (mục 17); theo tài liệu Vercel, mặc định gói hàm Python gồm mọi file của project.
- Cập nhật từ điển: khi `app/src/main/assets/databases/dictionary.db` thay đổi, chép đè lên `ai-service/data/dictionary.db` (PowerShell, ở thư mục gốc repo): `Copy-Item app\src\main\assets\databases\dictionary.db ai-service\data\dictionary.db -Force`, rồi commit.
- Chạy test (PowerShell, ở thư mục gốc repo): AI Service `docker run --rm -v "${PWD}\ai-service:/app" -w /app python:3.12-slim sh -c "pip install -q -r requirements.txt -r requirements-dev.txt && python -m pytest -q"`; Backend `docker run --rm -v "${PWD}\backend:/app" -v lori-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 mvn -B test '-Dtest=AiServiceClientTest,AiGatewayServiceTest'`. `AuthControllerIntegrationTest` dùng Testcontainers nên không chạy được trong lệnh này.

### Giới hạn đã biết (không sửa)

- Chỉ tra đúng dạng chữ ghi trong từ điển, chưa quy về dạng gốc: dạng chia không có mục riêng (ví dụ `spoke`, `written`, `cheaper`) hoặc cụm không có mục riêng (ví dụ `look up`) cho `inDictionary = false`; phần lớn dạng chia khác (như `went`, `affected`) có mục riêng nên vẫn là `true`.
- `pos` là mã của từ điển (`N`, `V`, `A`, ...), chưa đổi thành tên đầy đủ; chỉ lấy 5 nghĩa đầu của mỗi từ.
- Từ điển chỉ có đồng nghĩa/trái nghĩa cho 1.573 trong 118.356 từ; với các từ còn lại danh sách do model sinh, chưa được kiểm chứng.
- `explanation`, `collocations` và `difference` do LLM sinh, chưa có kiểm tra output (Tuần 15); nghĩa tiếng Việt chỉ có khi từ nằm trong từ điển.
- Nếu LLM trả JSON bị cắt do hết `max_output_tokens` hoặc sai cấu trúc thì AI Service trả `502` và Backend trả `503 AI service unavailable`; tần suất đo ở Tuần 12 Ngày 7 (mục 23).
- Chưa có ví dụ câu theo level và mini exercise (Module 5.7, Tuần 13); chưa ghi `ai_feedback_metadata`; chưa có màn hình Android riêng cho Vocabulary Tutor (UI mẫu v7 không có).
- `ai-service/data/dictionary.db` là bản sao, không tự cập nhật theo bản trên Android; repo tăng thêm khoảng 37 MB.

## 23. Kiểm thử Grammar, Vocabulary và UI AI Tutor (Tuần 12 – Ngày 7)

### Công cụ

- `tools/ai-test/grammar_vocabulary_test.py`: chạy bằng Docker (image `python:3.12-slim`, chỉ dùng thư viện chuẩn của Python), đi qua đường thật Script → Render (Backend) → Vercel (AI Service) → Cloudflare Workers AI, không dùng mock. Biến môi trường (chỉ ghi tên): `BASE_URL` (URL Render, không có `/` ở cuối), `API_KEY`, `APP_SIGNATURE`. Lệnh chạy (PowerShell, ở thư mục gốc repo, sau khi đặt 3 biến môi trường):
  `docker run --rm -e BASE_URL -e API_KEY -e APP_SIGNATURE -v "${PWD}\tools\ai-test:/work" -w /work python:3.12-slim python -u grammar_vocabulary_test.py`
- Mỗi lần chạy: đăng ký user mới `grammar.<thời gian>@example.com` (Free) → kiểm tra `POST /api/ai/analyze-text` bị `403 PREMIUM_REQUIRED` → `mock-purchase` → 20 request `analyze-text` (10 ngữ pháp, 7 từ vựng, 3 đầu vào sai), cách nhau 3 giây để không chạm giới hạn 20 request/phút/user.
- Kết quả ghi ở `tools/ai-test/results/grammar-vocabulary-<ngày>-<giờ UTC>.md` (câu trả lời gốc của từng request). Phần tự động chỉ kiểm tra cấu trúc và các điều biết chắc (HTTP, `correct`, câu sửa khớp mẫu, `inDictionary` theo `dictionary.db`, `difference`); chất lượng câu sửa, giải thích và `lessonId` do người đọc file kết quả.

### Dữ liệu mẫu

| ID | Đầu vào | Kiểm tra tự động |
|---|---|---|
| G1–G8 | 8 câu sai (chủ-vị, thì quá khứ, mạo từ, hiện tại hoàn thành, câu điều kiện, bị động, so sánh hơn, câu nhiều lỗi) | `correct = false`, câu sửa khớp mẫu, G8 có ≥ 2 lỗi |
| G9–G10 | 2 câu đúng | `correct = true`, `errors` rỗng, `lessonId = null` |
| V1 | `improve` | 1 từ, trong từ điển, `difference = null` |
| V2–V4 | `affect, effect` / `make vs do` / `borrow / lend` | 2 từ, trong từ điển, có `difference` (thử 3 kiểu ngăn cách: dấu phẩy, `vs`, `/`) |
| V5 | `begin` | đồng nghĩa có `start`, trái nghĩa có `end` lấy từ từ điển |
| V6–V7 | `look up`, `written` | không có trong từ điển: `inDictionary = false`, `ipa = null`, `definitions` rỗng |
| X1–X3 | `<b></b>`; `apple, banana, orange`; 41 chữ `a` | `400 Invalid input` |

Kỳ vọng `inDictionary` lấy từ chính `dictionary.db` hiện tại bằng `lookup_word` của AI Service.

### Kết quả kiểm thử ngày 27/10/2026

| Hạng mục | Kết quả |
|---|---|
| Premium gate `analyze-text` (user Free) | Đạt (`403 PREMIUM_REQUIRED`) |
| Kiểm tra tự động | 19/20 đạt (không đạt: V2 `affect, effect` trả HTTP 503 `AI service unavailable`, kỳ vọng 200) |
| Request trả `503` (model trả JSON không dùng được hoặc lỗi dịch vụ) | 1/20 (V2) |
| Đọc thủ công câu sửa và giải thích G1–G8 | Cả 8 câu sửa đúng ngữ pháp; G9, G10 đúng `correct = true`. 7/8 câu sửa tự nhiên; G4 sửa thành `has gone` là đúng dạng phân từ nhưng nghĩa là "đã đi và chưa về", với `twice` thì `has been` tự nhiên hơn. Nhãn loại lỗi của G2 và G8 (lỗi 1) ghi `Subject-verb agreement` trong khi lỗi thật là chia thì (quá khứ); giải thích G8 lỗi 1 vòng vo nhưng kết luận đúng. Giải thích G1 nói động từ "có đuôi s" hơi thiếu chính xác (trợ động từ `does` mới là chỗ chia). |
| `lessonId` G1–G8 so với bài tham khảo | 8/8 đúng bài tham khảo (G1→1, G2→3, G3→7, G4→11, G5→19, G6→17, G7→14, G8→3); không có câu lệch |
| Đọc thủ công V1–V7 | V2 không có dữ liệu (503) nên chỉ đọc V1, V3–V7. Không có giải thích nào mâu thuẫn với nghĩa tiếng Việt trong từ điển; `difference` của V3, V4 hợp lý, collocation nhìn chung tự nhiên (hơi lạ: `lend time`, `begin today`). Điểm yếu: V3 `do` có đồng nghĩa `hair-do`, `hairdo`, `doh`, `ut` và trái nghĩa `don'ts`, có vẻ lấy từ dữ liệu từ điển theo nghĩa danh từ nên không hợp với động từ `do`; V4 đồng nghĩa `give`, `offer` của `lend` khá lỏng; V5 `explanation` bằng tiếng Việt trong khi các từ khác bằng tiếng Anh. Phát âm: V4 `borrow` ở dạng respelling, V5 `begin` không có phát âm (dữ liệu gốc). V6 (`look up`) và V7 (`written`) đúng `inDictionary = false`, giải thích hợp lý. X1–X3 đều `400 Invalid input` như kỳ vọng. |

### Kết quả kiểm thử UI trên LDPlayer 9 (APK 1.3-alpha, ngày 27/10/2026)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Free bấm thẻ AI English Tutor → dialog Premium | Đạt |
| 2 | Mua Premium mock → vào AI Tutor Home | Đạt |
| 3 | Bố cục AI Tutor Home, 3 thẻ "Sắp ra mắt" không bấm được | Đạt |
| 4 | Hỏi đáp: gửi, bong bóng đang trả lời, nhận câu trả lời | Đạt |
| 5 | Hỏi đáp: hiển thị Markdown | Đạt |
| 6 | Hỏi đáp: offline khóa nút Gửi | Đạt |
| 7 | Phân tích câu: ô trống báo lỗi | Đạt |
| 8 | Phân tích câu: câu sai (câu gốc, câu đúng, giải thích, bài liên quan) | Đạt |
| 9 | Phân tích câu: câu đúng hiện banner | Đạt |
| 10 | Phân tích câu: đầu vào không hợp lệ (400) | Đạt |
| 11 | Phân tích câu: offline khóa nút Kiểm tra | Đạt |
| 12 | Premium hết hạn giữa chừng ở Hỏi đáp → dialog | Đạt |
| 13 | Premium hết hạn giữa chừng ở Phân tích câu → dialog | Đạt |

Lỗi phát hiện: không có lỗi ở UI (13/13 kịch bản đạt). Ngoài UI: V2 trả `503` một lần ở lần chạy script (xem bảng trên).

### APK

- `app/build.gradle.kts`: `versionCode = 4`, `versionName = "1.3-alpha"`. APK debug build trên máy Dev, **không commit** (`*.apk` nằm trong `.gitignore`).
- APK debug ký bằng debug keystore của máy build, khớp `APP_SIGNATURE` trên Render; build trên máy khác thì chữ ký khác và Backend trả 403.

### Dấu vết để lại sau mỗi lần chạy

- Mỗi lần chạy script: 1 user thử và 1 subscription `MOCK` trong Neon (không tự xóa). Test UI để lại 1 user thử `lori.ui.<ngày>@example.com` với `premium_expires_at` đã bị đặt về quá khứ.
- Các request LLM dùng hạn mức Neurons Cloudflare (10.000 Neurons/ngày); chưa đo số Neurons thực tế của từng lần chạy.

### Giới hạn đã biết (không sửa)

- Test UI chỉ làm trên LDPlayer 9 (dùng mạng của máy tính), **chưa test bằng điện thoại thật và mạng 4G**. App gọi URL công khai của Render nên về nguyên tắc không phụ thuộc mạng của server, nhưng chưa kiểm chứng trên mạng di động.
- Chưa có màn hình Android cho Vocabulary Tutor; Vocabulary chỉ được kiểm thử qua API.
- Bộ mẫu chỉ 20 request, không đủ để kết luận tỉ lệ chính xác. Kết quả LLM có thể đổi giữa các lần chạy hoặc khi Cloudflare cập nhật model.
- Một số từ trong `dictionary.db` có phát âm kiểu respelling (ví dụ `borrow` → `bŏrʹō`) hoặc không có phát âm (ví dụ `begin`); AI Service lấy nguyên văn từ từ điển.
- Kịch bản Premium hết hạn giữa chừng (12-13) chỉ làm được bằng cách sửa `users.premium_expires_at` trong Neon.

## 24. Phân tích Quiz và Exam bằng AI (Tuần 13 – Ngày 3-4)

- Hai endpoint (yêu cầu đăng nhập và Premium, `@RequirePremium` ở cấp class của `AiGatewayController`):
  - `POST /api/ai/analyze-quiz`. Request: `{"quizType":"grammar","itemId":26}`; `quizType` là `vocabulary` (quiz theo chủ đề từ vựng) hoặc `grammar` (quiz theo bài ngữ pháp), `itemId` là id chủ đề / bài ngữ pháp, giống `item_id` của `user_progress`. Response `200`: `{"type":"quiz_analysis","title":"Subjunctive Mood","correctCount":2,"incorrectCount":8,"accuracyPercent":20,"summary":"...","patterns":["..."],"recommendations":[{"title":"...","reason":"...","lessonId":26}]}`.
  - `POST /api/ai/analyze-exam`. Request: `{"resultId":"<id kết quả thi>"}` (`id` trong response của `POST /api/exams/{id}/submit`). Response `200`: `{"type":"exam_analysis","resultId":"...","parts":[{"name":"Part 5 - Incomplete Sentences","skill":"READING","correctCount":18,"totalCount":30,"accuracyPercent":60}],"summary":"...","patterns":["..."],"recommendations":[{"title":"...","reason":"...","lessonId":null}]}`; `parts` yếu nhất trước.
- Nguyên tắc: Backend chỉ **đọc** kết quả đã chấm, không chấm lại. Mọi con số (số câu đúng/sai, %, thống kê từng phần) do Backend tính và tự đặt vào response; AI chỉ viết `summary` (2-3 câu), `patterns` (tối đa 3, có thể rỗng) và `recommendations` (tối đa 3, quan trọng nhất trước). `lessonId` là id bài ngữ pháp trong `dictionary.db` hoặc `null`; AI Service bỏ liên kết (đặt `null`) nếu model đưa id không có trong danh sách bài của Lori.
- Luồng quiz: JWT → `@RequirePremium` → Bean Validation → `AiRateLimiter.checkAnalyzeQuiz` (bucket riêng 20 request/phút/user) → đọc `user_progress` (kết quả lần làm gần nhất; không có hoặc chưa trả lời câu nào thì `404 Quiz result not found`) → đọc `user_weakness_record` (số liệu cộng dồn và trạng thái `learning/reviewing/mastered`, `null` nếu chưa có) → `LearningContextService.build(userId, ANALYZE_QUIZ)` (điểm yếu ngữ pháp và từ vựng, mục 18) → `AiServiceClient.analyzeQuiz` (ký HMAC, mục 17) → AI Service `POST /ai/analyze-quiz` → Cloudflare Workers AI → làm sạch JSON.
- Luồng exam: tương tự nhưng `AiRateLimiter.checkAnalyzeExam` (bucket riêng) → `ExamService.getResultDetail` (kết quả của user khác hoặc không tồn tại thì `404 Result not found`) và `ExamService.getExam` → gom theo phần (TOEIC gom `Part 3 - Conversation 1..n` thành `Part 3 - Conversation` bằng cách bỏ số thứ tự và khoảng `(Qx-Qy)` ở tiêu đề section; IELTS giữ từng section) và theo loại câu hỏi, sắp xếp yếu nhất trước → `LearningContextService.build(userId, ANALYZE_EXAM)` (thêm `recentExams`, mục 18) → AI Service `POST /ai/analyze-exam`. Gửi kèm tối đa 10 câu sai (có nội dung chữ, cắt 200 ký tự) từ các phần yếu nhất; đáp án dạng chữ cái được ghép nội dung lựa chọn (ví dụ `B: wrote`).
- Backend không gửi `userId`, `resultId` hay id đề sang AI Service. Không thêm biến môi trường, thư viện hay migration so với mục 17.
- Mã lỗi: `400` (body sai, ví dụ `quizType` không phải `vocabulary`/`grammar`), `401` (chưa đăng nhập), `403 PREMIUM_REQUIRED` (user Free), `404` (quiz chưa đồng bộ lên server / kết quả thi không phải của user hoặc không tồn tại), `429` (quá 20 request/phút/user cho từng tác vụ), `503 AI service unavailable` (AI Service hoặc Cloudflare lỗi, model trả JSON sai cấu trúc, hết hạn mức 10.000 Neurons/ngày, mục 17, quá 30 giây). Timeout như mục 19: AI Service chờ Cloudflare tối đa 25 giây, Backend chờ AI Service tối đa 30 giây.
- Chạy test (PowerShell, ở thư mục gốc repo): AI Service như mục 22; Backend `docker run --rm -v "${PWD}\backend:/app" -v lori-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 mvn -B test '-Dtest=AiExamAnalysisServiceTest,AiQuizAnalysisServiceTest,AiRateLimiterTest,AiServiceClientTest,AiGatewayServiceTest'`.

### Kiểm thử trên Render (Postman T13-T18)

| # | Kịch bản | Request | Kết quả kỳ vọng |
|---|---|---|---|
| 1 | Quiz ngữ pháp | T13: `grammar`, bài 26 | `200`, `title` = `Subjunctive Mood`, 2 đúng / 8 sai, 20% |
| 2 | Quiz từ vựng | T14: `vocabulary`, chủ đề 13 | `200`, `title` = `Giáng sinh`, 3 đúng / 7 sai, 30% |
| 3 | Quiz chưa đồng bộ | T15: `grammar`, bài 1 | `404 Quiz result not found` |
| 4 | Phân tích bài thi | T16: `resultId` của bài thi trắng ở T11 | `200`, `type` = `exam_analysis`, mọi phần 0 câu đúng |
| 5 | Kết quả thi không tồn tại | T17: `resultId` ngẫu nhiên | `404 Result not found` |
| 6 | `quizType` sai | T18: `listening` | `400`, thông điệp bắt đầu bằng `quizType:` |

Kết quả chạy: ghi `Đạt`/`Không đạt` và ngày chạy thực tế sau khi chạy xong.

### Giới hạn đã biết (không sửa)

- Server chỉ có số câu đúng/sai của quiz (app Android chấm rồi đồng bộ), không có từng câu sai; AI chỉ nhận định theo số liệu này, kết quả lần làm gần nhất và số liệu cộng dồn, không nêu được câu cụ thể. Nâng cấp khi app đồng bộ thêm chi tiết từng câu.
- Phải đồng bộ tiến trình (`POST /api/progress/sync`) trước khi gọi `analyze-quiz`; quiz chưa đồng bộ trả `404`. Quiz làm trước khi có Learning Engine (Tuần 13 – Ngày 1-2) không có số liệu cộng dồn nên chỉ phân tích theo lần làm gần nhất.
- Phân tích bài thi chỉ gửi tối đa 10 câu sai, mỗi câu tối đa 200 ký tự, chỉ câu có chữ; câu chỉ có audio/hình (TOEIC Part 1-2) chỉ nằm trong thống kê phần. Cách gom TOEIC phụ thuộc tiêu đề section dạng `Part N - ... (Qx-Qy)` do `tools/exam-import/toeic_to_sql.py` sinh; tiêu đề khác dạng đó được giữ nguyên.
- Mỗi lần gọi là một lần gọi LLM: chưa cache, chưa ghi `ai_feedback_metadata`; số Neurons mỗi lần chưa đo.
- Postman T16 chạy với đề đầu tiên trong `GET /api/exams` (IELTS) và bài trắng; cách gom phần của TOEIC chỉ được kiểm bằng unit test, chưa chạy trên Render.
- `summary`, `patterns`, `recommendations` do LLM sinh bằng tiếng Anh đơn giản (theo prompt), chưa có kiểm tra output (Tuần 15); Postman chỉ kiểm tra cấu trúc, không kiểm tra nội dung đúng sai.
- Chưa có màn hình Android cho phân tích quiz/bài thi.

## 25. AI Generated Exercises và Personalized Review (Tuần 13 – Ngày 5-6)

- Hai endpoint (yêu cầu đăng nhập và Premium, `@RequirePremium` ở cấp class của `AiGatewayController`):
  - `POST /api/ai/generate-exercise`. Request: `{"category":"grammar","topicId":26}`; `category` là `grammar` (bài ngữ pháp) hoặc `vocabulary` (chủ đề từ vựng), `topicId` là id bài / chủ đề, giống `itemId` của `analyze-quiz`. Response `200`: `{"type":"exercise_set","exercises":[{"type":"multiple_choice","question":"...","options":["...","...","...","..."],"answerIndex":0,"explanation":"..."},{"type":"fill_blank","question":"She ___ to school.","answers":["goes"],"explanation":"..."}]}`; 1-5 bài mỗi lần.
  - `POST /api/ai/recommend`. Không có body. Response `200`: `{"type":"recommendation","topics":[{"category":"grammar","topicKey":"26","topicName":"Subjunctive Mood","correctCount":2,"incorrectCount":8,"accuracyPercent":20}],"summary":"...","recommendations":[{"title":"...","reason":"...","lessonId":26}]}`. Chưa có chủ đề yếu: `topics` và `recommendations` rỗng, `summary` = `null`, không gọi AI.
- Nguyên tắc: chủ đề yếu và mọi con số do Learning Engine (code) chọn và tính (mục 18, Tuần 13 – Ngày 1-2: tối đa 3 chủ đề `reviewing`, tỉ lệ đúng thấp nhất trước); AI chỉ viết `summary` (2-3 câu) và `recommendations` (mỗi chủ đề yếu một đề xuất, đúng thứ tự). `lessonId` là id bài ngữ pháp trong `dictionary.db` hoặc `null` (luôn `null` với chủ đề từ vựng); AI Service đặt `null` nếu model đưa id không có trong danh sách bài của Lori.
- Luồng generate-exercise: JWT → `@RequirePremium` → Bean Validation → `AiRateLimiter.checkGenerateExercise` (bucket riêng 20 request/phút/user) → `LearningContextService` đổi id thành tên (`learning-names.json`; id không có thì `400 Unknown topic`) → đọc tối đa 10 câu đã sinh gần nhất của user cho chủ đề này (`ai_generated_exercise.source_weakness` = `grammar:<id>` / `vocabulary:<id>`) và trình độ ước tính (`LearningEngineService.getLearningProfile`, dùng làm độ khó) → `LearningContextService.build(userId, GENERATE_EXERCISE)` → `AiServiceClient.generateExercise` (ký HMAC, mục 17) → AI Service `POST /ai/generate-exercise` → Cloudflare Workers AI → AI Service kiểm tra từng bài → Backend lưu mỗi bài một dòng vào `ai_generated_exercise` với `is_validated = false`.
- Kiểm tra trước khi lưu (AI Service, `exercise_workflow.py`): `multiple_choice` đúng 4 lựa chọn khác nhau, không rỗng, `answerIndex` từ 0 đến 3; `fill_blank` đúng một chỗ trống `___`, 1-3 đáp án; bài sai định dạng hoặc trùng câu đã sinh trước đó bị bỏ, không còn bài hợp lệ nào thì `503`. Câu đã sinh trước đó được gửi kèm vì model chạy `TEMPERATURE=0` (gọi lại sẽ ra y hệt nếu không loại trùng).
- Luồng recommend: tương tự với `AiRateLimiter.checkRecommend` (bucket riêng) → `LearningEngineService.getWeaknesses` (lấy 3 chủ đề đầu) → `LearningContextService.build(userId, RECOMMEND)` → AI Service `POST /ai/recommend`.
- Backend không gửi `userId` sang AI Service. Không thêm biến môi trường, thư viện hay migration so với mục 17 (bảng `ai_generated_exercise` có từ V5).
- Mã lỗi: `400` (body sai, ví dụ `category` không phải `grammar`/`vocabulary`, `topicId` thiếu hoặc không dương, hoặc `Unknown topic`), `401`, `403 PREMIUM_REQUIRED` (user Free), `429` (quá 20 request/phút/user cho từng tác vụ), `503 AI service unavailable` (AI Service hoặc Cloudflare lỗi, model trả JSON sai cấu trúc hoặc không còn bài hợp lệ, hết hạn mức 10.000 Neurons/ngày, mục 17, quá 30 giây).
- Chạy test (PowerShell, ở thư mục gốc repo): AI Service như mục 22; Backend `docker run --rm -v "${PWD}\backend:/app" -v lori-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 mvn -B test '-Dtest=AiExerciseServiceTest,AiRecommendServiceTest,AiRateLimiterTest,AiServiceClientTest'`.

### Kiểm thử trên Render (Postman T19-T23)

| # | Kịch bản | Request | Kết quả kỳ vọng |
|---|---|---|---|
| 1 | Sinh bài tập ngữ pháp | T19: `grammar`, bài 26 | `200`, `type` = `exercise_set`, 1-5 bài đúng định dạng |
| 2 | Sinh bài tập từ vựng | T20: `vocabulary`, chủ đề 13 | `200`, như trên |
| 3 | Chủ đề không tồn tại | T21: `grammar`, `99999` | `400 Unknown topic` |
| 4 | `category` sai | T22: `listening` | `400`, thông điệp bắt đầu bằng `category:` |
| 5 | Đề xuất ôn tập | T23 | `200`, `topics` = `Subjunctive Mood` (20%), `Giáng sinh` (30%), có `summary` |

Kết quả chạy ngày 30/10/2026: Đạt (T19, T20, T21, T22, T23 đều đạt, 5/5 request).

### Giới hạn đã biết (không sửa)

- Kết quả làm bài tập của user chưa được ghi lại (chưa có endpoint nộp bài tập); `POST /api/ai/generate-exercise` chỉ sinh và lưu bài. Nâng cấp khi app có luồng làm bài tập.
- Mọi bài tập lưu với `is_validated = false`; việc review và đánh dấu `true` là của Tuần 13 – Ngày 7. API chưa lọc theo `is_validated` (chưa có chỗ dùng).
- Bài tập sinh lại mỗi lần gọi: không tái sử dụng bài đã lưu, chưa cache; chỉ tránh lặp với tối đa 10 câu gần nhất của cùng user và chủ đề.
- Độ khó chỉ dựa trên trình độ ước tính thô của Learning Engine (mục 18); user chưa có dữ liệu ngữ pháp được coi là `A1`.
- Kiểm tra output chỉ về định dạng; chưa kiểm tra đáp án có đúng ngữ pháp / nghĩa hay không (phần kiểm tra output nâng cao là Tuần 15, review nội dung là Tuần 13 – Ngày 7).
- Mỗi lần gọi là một lần gọi LLM, chưa ghi `ai_feedback_metadata`; số Neurons mỗi lần chưa đo. `recommend` chỉ xét tối đa 3 chủ đề yếu nhất.
- `summary`, `recommendations` do LLM sinh bằng tiếng Anh đơn giản; Postman chỉ kiểm tra cấu trúc, không kiểm tra nội dung đúng sai.