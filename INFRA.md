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

> Kế hoạch gốc dùng Google Cloud Run; vì team không dùng thẻ tín dụng nên Backend chạy trên **Render**.
> Kế hoạch gốc đặt AI Service trên VM Oracle Cloud Always Free (Docker Compose, DuckDNS, Caddy); vì team không dùng thẻ tín dụng nên AI Service chạy trên **Vercel Hobby**. Vercel tự cấp HTTPS và tên miền `*.vercel.app`.

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
- **Trên Render chưa có LibreTranslate** (Render không với tới máy Dev) → `POST /api/translate` trả `503 Translation service unavailable`; các API khác không bị ảnh hưởng. Chỗ chạy cố định sẽ chốt ở Tuần 9 (cùng AI Service).

### Thanh toán mock và hết hạn Premium

- `POST /api/payment/mock-purchase` (`{"planType":"MONTHLY|YEARLY|LIFETIME"}`) tạo bản ghi `subscriptions` (`payment_method = MOCK`) và bật Premium; không có giao dịch tiền thật. Google Play / VNPay / Momo là optional, chưa làm.
- Biến môi trường (đều tùy chọn): `PAYMENT_MOCK_ENABLED` (mặc định `true`; `false` thì mock-purchase trả 403), `SUBSCRIPTION_EXPIRY_CRON` (mặc định `0 0 * * * *` = đầu mỗi giờ).
- `SubscriptionExpiryScheduler` chạy theo lịch trên, đánh dấu gói `EXPIRED` và hạ `users.is_premium` của user hết hạn. Premium vĩnh viễn (`expires_at` rỗng) không bị hạ. Mỗi lần chạy sẽ đánh thức Neon (đang tự ngủ khi không có request).

## 13. Kiểm thử API (Postman Collection v2, load test k6)

### Postman Collection v2

- File: `postman/Lori-API-v2.postman_collection.json` (Collection v2.1). Bản v1 (`postman/Lori-Auth.postman_collection.json`) giữ lại để tham khảo.
- Gồm 2 thư mục:
  - `1. Auth flow`: register → login → truy cập API cần đăng nhập → refresh (xoay vòng) → logout, kèm các trường hợp 401.
  - `2. Premium flow`: register → login → user Free gọi `GET /api/exams` bị `403 PREMIUM_REQUIRED` → `POST /api/payment/mock-purchase` → gọi lại `GET /api/exams` thành công bằng cùng access token.
- Cần một Environment có 3 biến: `baseUrl` (URL Render, không có `/` ở cuối), `apiKey`, `appSignature`. **Không commit file Environment** (chứa khóa). Header `X-API-Key` và `X-App-Signature` được Pre-request script của collection thêm vào mọi request.
- Chạy: Postman → chuột phải thư mục (hoặc collection) → Run. Mỗi lần chạy đăng ký một email mới `postman.<thời gian>@example.com` nên để lại user thử trong Neon. Một lần chạy cả collection dùng 14 request.
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

| Method | Đường dẫn | Xác thực | Mô tả |
|---|---|---|---|
| GET | `/` | Không | Trả `{"message":"Hello from Lori AI Service"}`; UptimeRobot theo dõi đường dẫn này (mục 7) |
| POST | `/ai/ping` | Chữ ký HMAC | Trả `{"status":"ok"}`; Backend gọi qua `AiServiceClient.ping()` |

### Xác thực Server-to-Server

- Backend ký mỗi request gửi sang AI Service bằng HMAC-SHA256, khóa là `AI_SERVICE_SHARED_SECRET`. Chuỗi được ký: `timestamp \n METHOD \n path \n sha256-hex(body)`. Chữ ký (hex) nằm ở header `X-Lori-Signature`, thời điểm (giây epoch) ở `X-Lori-Timestamp`.
- AI Service (`ai-service/guardrails/s2s_auth.py`) tính lại chữ ký, so sánh bằng `hmac.compare_digest` và chỉ nhận request lệch thời gian không quá 300 giây. Thiếu header, sai chữ ký, timestamp sai định dạng hoặc hết hạn → `401 Unauthorized`.
- Fail closed: nếu `AI_SERVICE_SHARED_SECRET` chưa đặt hoặc ngắn hơn 32 ký tự, AI Service từ chối mọi request cần chữ ký (401). Phía Backend, `AiServiceClient` trả `503 AI service unavailable` khi chưa có secret hoặc khi AI Service lỗi/từ chối.
- Công thức ký được khóa bằng một test vector chung: `AiServiceSignerTest` (Backend) và `test_s2s_auth.py` (AI Service) phải cho cùng một chữ ký.

### Giới hạn đã biết (không sửa)

- Vercel Hobby: mỗi tháng 1.000.000 lần gọi hàm, 4 CPU-giờ hoạt động, 360 GB-giờ bộ nhớ; chỉ dùng cho mục đích phi thương mại. Vượt hạn mức thì tính năng bị tạm dừng, thường phải chờ 30 ngày.