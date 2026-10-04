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

> Kế hoạch gốc dùng Google Cloud Run; vì team không dùng thẻ tín dụng nên Backend chạy trên **Render**.

## 2. Neon (PostgreSQL)

1. Tạo tài khoản tại https://neon.tech (không cần thẻ tín dụng).
2. Tạo project, trong đó có database `lori_db`. Phiên bản Postgres: **16**.
3. Neon có 2 loại chuỗi kết nối:
   - **Pooled** (host có đuôi `-pooler`): dùng cho ứng dụng nhiều kết nối ngắn.
   - **Direct** (host không có `-pooler`): dùng cho công cụ quản trị như `pg_dump`/`pg_restore`.
4. Kiểm tra kết nối từ máy local bằng `psql` với chuỗi kết nối của `lori_db`.
5. Chuỗi kết nối chứa mật khẩu → chỉ để trong biến môi trường Render và GitHub Secret, không commit.

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
| `NEON_URL` | Kết nối PostgreSQL (Neon) |
| `UPSTASH_URL` | Kết nối Redis (Upstash) |
| `JWT_SECRET` | JWT secret |

- Auto-Deploy (Render → Settings → Build & Deploy): **Off**.

## 5. CI/CD (GitHub Actions → Render)

File: `.github/workflows/backend-deploy.yml`, tên workflow **Backend CI/CD**.

- **Kích hoạt:** push lên nhánh `main` có thay đổi trong `backend/**` hoặc chính file workflow. Thay đổi ở nơi khác (ví dụ `INFRA.md`, thư mục `app/`) **không** kích hoạt deploy.
- **Job `build`:** `docker build` thư mục `backend` để bắt lỗi build sớm.
- **Job `deploy`** (chạy sau `build`): gọi **Deploy Hook** của Render bằng `curl -X POST`.
- **GitHub Secret (repo Lori):** `RENDER_DEPLOY_HOOK_URL` — URL Deploy Hook lấy từ Render → service → Settings. Không để URL này trong file.
- **Kiểm tra:** push một thay đổi nhỏ trong `backend/` → tab Actions của repo Lori chạy xanh → Render hiện một lần deploy mới.