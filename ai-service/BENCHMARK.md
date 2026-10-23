# BENCHMARK.md — Model LLM ứng viên cho AI Service

> Danh sách model ứng viên cho AI Service. Số liệu benchmark chỉ được ghi khi đã đo thật.
> Model chạy trên Cloudflare Workers AI (gói Workers Free, không cần thẻ); xem `INFRA.md` mục 17.
> Giá Neuron và context lấy từ tài liệu Cloudflare ngày 22/10/2026, có thể thay đổi.

## 1. Tiêu chí chọn ứng viên

- Open-weight, instruction-tuned, dùng được ở gói Workers Free (không cần form xin quyền, không cần gói Workers Paid).
- Cỡ nhỏ đến vừa; có bản nhỏ để làm ứng viên model fallback.
- Không chọn model chuyên code, vision hoặc kiểm duyệt nội dung.
- Chi phí Neuron thấp để benchmark và demo nằm trong hạn mức 10.000 Neurons/ngày.
- Gọi thành công qua REST API (đã thử ở Tuần 10 Ngày 1-2).

## 2. Danh sách ứng viên

| # | Model ID | Hãng | Cỡ | Context | Neuron / 1 triệu token (vào / ra) | ≈ Neuron / request* | Ghi chú |
|---|---|---|---|---|---|---|---|
| 1 | `@cf/meta/llama-3.2-1b-instruct` | Meta | 1B | 60.000 | 2.457 / 18.252 | 6,7 | Nhỏ nhất, ứng viên fallback |
| 2 | `@cf/meta/llama-3.2-3b-instruct` | Meta | 3B | 80.000 | 4.625 / 30.475 | 11,5 | |
| 3 | `@cf/meta/llama-3.1-8b-instruct-fp8` | Meta | 8B (FP8) | 32.000 | 13.778 / 26.128 | 14,7 | |
| 4 | `@cf/google/gemma-4-26b-a4b-it` | Google | 26B tổng, 4B hoạt động (MoE, theo tên model) | 256.000 | 9.091 / 27.273 | 12,7 | Có reasoning |
| 5 | `@cf/qwen/qwen3-30b-a3b-fp8` | Qwen | 30B tổng, 3B hoạt động (MoE, theo tên model) | 32.768 | 4.625 / 30.475 | 11,5 | Có reasoning |

\* Ước tính cho 1 request gồm 500 token vào và 300 token ra; chưa tính token suy luận của model có reasoning.

## 3. Phạm vi benchmark tài nguyên trên Cloudflare Workers AI

> Kế hoạch (mục 4.3, Tuần 10 Ngày 3-4) yêu cầu đo RAM, CPU, dung lượng đĩa, thời gian nạp model và so sánh quantization trên VM Oracle. Dự án chạy LLM trên Cloudflare Workers AI (model do Cloudflare chạy), nên các mục đó được xử lý như bảng dưới. Thông tin về model lấy từ tài liệu Cloudflare ngày 23/10/2026.

| Hạng mục trong kế hoạch | Đo được trên Workers AI? | Cách xử lý |
|---|---|---|
| Peak RAM | Không | Không có số đo |
| CPU utilization | Không | Không có số đo |
| Model size on disk | Không | Dùng số tham số (cột "Cỡ" ở mục 2) và mức quantization theo tài liệu |
| Model load time | Không đo riêng được | Ảnh hưởng của nó (nếu có) nằm trong cold start, đo ở Ngày 5 |
| So sánh ≥2 mức quantization | Không tự chọn được | So sánh các biến thể của Llama 3.1 8B mà Cloudflare cung cấp (mục 5.3) |
| Chi phí tài nguyên (Cost benchmark, kế hoạch mục 4.3) | Có | Đo token và Neurons/request (mục 5) |

Mức quantization theo Cloudflare: `llama-3.1-8b-instruct-fp8` và `qwen3-30b-a3b-fp8` là FP8; `llama-3.2-1b-instruct`, `llama-3.2-3b-instruct` và `gemma-4-26b-a4b-it` không có mức quantization trong tên model và trang model Cloudflare không nêu mức này.

## 4. Cách chạy benchmark Neurons

- Script: `tools/llm-benchmark/resource_benchmark.py` (chỉ dùng thư viện chuẩn của Python, chạy trong Docker `python:3.12-slim`).
- Biến môi trường (chỉ ghi tên, không ghi giá trị): `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN`.
- Lệnh chạy (PowerShell, ở thư mục gốc repo):

```powershell
$env:CLOUDFLARE_ACCOUNT_ID = Read-Host "Account ID"
$env:CLOUDFLARE_API_TOKEN = Read-Host "API token"
docker run --rm -e CLOUDFLARE_ACCOUNT_ID -e CLOUDFLARE_API_TOKEN -v "${PWD}\tools\llm-benchmark:/work" -w /work python:3.12-slim python -u resource_benchmark.py
```

- Mỗi lần chạy: 7 model × 5 prompt × 3 lần = tối đa 105 request; `max_tokens` 1024, `temperature` 0. Tối đa khoảng 4.100 Neurons (khi mọi request dùng hết 1024 token ra), trong hạn mức 10.000 Neurons/ngày (reset 00:00 UTC).
- Neurons mỗi request = (token vào × đơn giá vào + token ra × đơn giá ra) / 1.000.000. Token lấy từ trường `usage` mà Cloudflare trả về; đơn giá lấy từ trang pricing của Cloudflare (kiểm tra 23/10/2026). Neurons là số tính từ token và đơn giá, không phải số đọc trực tiếp từ Cloudflare.
- Kết quả mỗi lần chạy lưu ở `tools/llm-benchmark/results/resource-<ngày>-<giờ UTC>.csv`.