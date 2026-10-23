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

## 5. Kết quả benchmark Neurons

### 5.1 Thông tin lần chạy

- Ngày giờ chạy: `20261023-021831`, tức 23/10/2026 02:18:31 UTC (lấy từ tên file CSV; các request được gọi từ 02:08:08 đến 02:18:31 UTC).
- Cách đo: 5 prompt × 3 lần mỗi model, `max_tokens` 1024, `temperature` 0; Neurons = token (từ `usage` của Cloudflare) × đơn giá Neuron của Cloudflare.
- Dữ liệu thô: `tools/llm-benchmark/results/resource-20261023-021831.csv`.
- Tổng Neurons của lần chạy (theo script): `1446.47`.

### 5.2 Neurons theo model

Tổng hợp theo model (bộ 5 prompt này):

| Model | Số request | Token vào TB | Token ra TB | Neurons/request TB | Neurons/request lớn nhất | Bị cắt (length) | Số request/ngày (10.000 Neurons ÷ TB) |
|---|---|---|---|---|---|---|---|
| `@cf/meta/llama-3.2-1b-instruct` | 15 | 120 | 243 | 4.73 | 13.18 | 0 | 2114 |
| `@cf/meta/llama-3.2-3b-instruct` | 15 | 139 | 255 | 8.41 | 20.78 | 0 | 1189 |
| `@cf/meta/llama-3.1-8b-instruct-fp8` | 15 | 120 | 279 | 8.94 | 19.85 | 0 | 1118 |
| `@cf/google/gemma-4-26b-a4b-it` | 15 | 124 | 885 | 25.27 | 30.65 | 6 | 396 |
| `@cf/qwen/qwen3-30b-a3b-fp8` | 15 | 119 | 757 | 23.63 | 32.56 | 5 | 423 |

Ghi chú:

- Số request/ngày là con số ước tính theo đúng bộ 5 prompt này, không phải dự báo tải thật của người dùng.
- `llama-3.2-1b-instruct` tốn ít Neurons nhất (4.73/request); `llama-3.2-3b-instruct` (8.41) và `llama-3.1-8b-instruct-fp8` (8.94) ở mức gần nhau; `gemma-4-26b-a4b-it` (25.27) và `qwen3-30b-a3b-fp8` (23.63) tốn nhiều nhất, khoảng gấp 3 lần hai model Llama 3.2 3B và 3.1 8B FP8.
- Hai model Gemma 4 và Qwen3 có số token ra trung bình (885 và 757) cao hơn nhiều so với 3 model Llama (243 đến 279). `gemma-4-26b-a4b-it` có 6/15 request bị cắt do chạm 1024 token (cả 3 lần của prompt `context` và cả 3 lần của prompt `analysis`); `qwen3-30b-a3b-fp8` có 5/15 request bị cắt (3 lần `context`, 2 lần `analysis`). Vì bị cắt ở 1024 token nên Neurons của hai model này ở các prompt đó là mức tối thiểu, câu trả lời nếu để chạy hết sẽ tốn nhiều hơn.
- Ba model Llama không có request nào bị cắt (số token ra lớn nhất: 684 với `llama-3.2-1b-instruct`, 636 với `llama-3.2-3b-instruct`, 610 với `llama-3.1-8b-instruct-fp8`). `llama-3.2-1b-instruct` và `llama-3.1-8b-instruct-fp8` không trả `finish_reason` nên cột này được tính theo số token ra.
- Chưa đánh giá chất lượng câu trả lời; việc đó thuộc benchmark Ngày 5. Chưa chốt model nào.

### 5.3 So sánh quantization của Llama 3.1 8B

| Biến thể | Quantization | Token ra TB | Neurons/request TB | Ghi chú |
|---|---|---|---|---|
| `@cf/meta/llama-3.1-8b-instruct-fp8` | FP8 | 279 | 8.94 | 15 request, không bị cắt |
| `@cf/meta/llama-3.1-8b-instruct-awq` | AWQ (int4) | không có số liệu | không có số liệu | không gọi được: HTTP 410, mã lỗi 5028, thông điệp "Model has been deprecated: @cf/meta/llama-3.1-8b-instruct-awq was deprecated on 2026-05-30" |
| `@cf/meta/llama-3.1-8b-instruct` | bản gốc (trang model không nêu độ chính xác) | 291 | 25.45 | 15 request, không bị cắt; Neurons/request lớn nhất 64.19 (prompt `context`); token vào TB 139, nhiều hơn bản FP8 (120); vẫn gọi được ngày 23/10/2026 dù Cloudflare đã thông báo ngừng hoạt động từ 30/05/2026 |

Chỉ so sánh mức tiêu thụ Neurons; chất lượng của từng biến thể chưa được đánh giá (Ngày 5). Với cùng một bộ prompt, số token ra trung bình của hai biến thể gọi được gần nhau (279 và 291) nhưng Neurons/request của bản gốc cao hơn bản FP8 khoảng 2.8 lần (25.45 so với 8.94).

## 6. Benchmark latency, throughput và concurrency

### 6.1 Cách đo

- Script: `tools/llm-benchmark/latency_benchmark.py` (dùng chung `bench_common.py` và `resource_benchmark.py`), chạy trong Docker `python:3.12-slim` như mục 4:

```powershell
docker run --rm -e CLOUDFLARE_ACCOUNT_ID -e CLOUDFLARE_API_TOKEN -v "${PWD}\tools\llm-benchmark:/work" -w /work python:3.12-slim python -u latency_benchmark.py
```

- Prompt: prompt `chat` của mục 4 (`What is the difference between 'make' and 'do'? Give me two examples for each.`), cùng system prompt như mục 4, `max_tokens` 1024, `temperature` 0, không dùng streaming (không đo time-to-first-token).
- Mỗi model gồm: 1 request đầu tiên (cold), 20 request tuần tự (warm), rồi 3 mức đồng thời 2, 5, 10; mỗi mức 2 đợt, mỗi đợt gửi đồng thời đúng số request của mức đó và đợi tất cả hoàn thành. Tổng 55 request/model.
- Cold: request đầu tiên của model trong lần chạy, khi không có lời gọi Workers AI nào trong ít nhất 30 phút trước đó. Mốc 30 phút do dự án tự chọn để các lần đo cùng điều kiện; Cloudflare không công bố thời gian model bị gỡ khỏi bộ nhớ nên giá trị này chưa chắc là cold start thật.
- Latency là thời gian từ lúc gửi request đến khi nhận xong toàn bộ phản hồi, đo tại máy chạy script (Windows 10, Docker Desktop), đã gồm đường truyền từ máy này đến Cloudflare.
- p50 và p95 tính theo nearest-rank trên 20 request warm thành công (p50 là giá trị thứ 10, p95 là giá trị thứ 19 khi sắp xếp tăng dần).
- Token/s của một request = token ra / latency của request đó, đã gồm thời gian xử lý prompt và độ trễ mạng; với model có reasoning, token ra gồm cả token suy luận. Cột Token/s là trung vị của 20 request warm.
- Throughput tổng ở mức đồng thời N = tổng token ra của các request thành công trong một đợt / thời gian của cả đợt, lấy trung bình hai đợt. Ở mức 1 = tổng token ra / tổng latency của các request warm thành công.
- Bị cắt: `finish_reason` là `length` hoặc số token ra chạm 1024.

### 6.2 Thông tin lần chạy

- Ngày giờ chạy: `20261023-100422` (23/10/2026, 10:04:22 UTC, tức 17:04:22 giờ Việt Nam; đây là thời điểm lưu file, lần chạy bắt đầu lúc 09:42:18 UTC).
- Dữ liệu thô: `tools/llm-benchmark/results/latency-20261023-100422.csv`.
- Tổng Neurons của lần chạy (theo script): 3377.02.

### 6.3 Kết quả

Request đơn lẻ:

| Model | Cold start (s) | Warm thành công | p50 (s) | p95 (s) | Token/s (trung vị) | Token ra TB | Bị cắt (length) | Lỗi |
|---|---|---|---|---|---|---|---|---|
| `@cf/meta/llama-3.2-1b-instruct` | 4.377 | 20/20 | 2.25 | 4.31 | 82.7 | 192 | 0 | 0 |
| `@cf/meta/llama-3.2-3b-instruct` | 1.074 | 20/20 | 1.02 | 1.25 | 158.3 | 164 | 0 | 0 |
| `@cf/meta/llama-3.1-8b-instruct-fp8` | 24.693 | 20/20 | 16.15 | 24.57 | 18.4 | 302 | 0 | 0 |
| `@cf/google/gemma-4-26b-a4b-it` | 17.533 | 20/20 | 18.08 | 34.85 | 48.7 | 857 | 8 | 0 |
| `@cf/qwen/qwen3-30b-a3b-fp8` | 7.478 | 20/20 | 7.29 | 8.32 | 87.7 | 638 | 0 | 0 |

Đồng thời (mức 1 là các request tuần tự ở pha warm):

| Model | Đồng thời | Request thành công | p50 (s) | Lớn nhất (s) | Throughput tổng (token/s) |
|---|---|---|---|---|---|
| `@cf/meta/llama-3.2-1b-instruct` | 1 | 20/20 | 2.25 | 4.32 | 74.6 |
| `@cf/meta/llama-3.2-1b-instruct` | 2 | 4/4 | 2.65 | 4.26 | 110.0 |
| `@cf/meta/llama-3.2-1b-instruct` | 5 | 10/10 | 2.12 | 4.68 | 208.4 |
| `@cf/meta/llama-3.2-1b-instruct` | 10 | 20/20 | 2.32 | 5.00 | 398.3 |
| `@cf/meta/llama-3.2-3b-instruct` | 1 | 20/20 | 1.02 | 1.86 | 149.6 |
| `@cf/meta/llama-3.2-3b-instruct` | 2 | 4/4 | 1.12 | 1.72 | 232.5 |
| `@cf/meta/llama-3.2-3b-instruct` | 5 | 10/10 | 1.06 | 1.32 | 631.5 |
| `@cf/meta/llama-3.2-3b-instruct` | 10 | 20/20 | 1.03 | 2.17 | 809.1 |
| `@cf/meta/llama-3.1-8b-instruct-fp8` | 1 | 20/20 | 16.15 | 31.06 | 17.0 |
| `@cf/meta/llama-3.1-8b-instruct-fp8` | 2 | 4/4 | 17.35 | 24.03 | 30.9 |
| `@cf/meta/llama-3.1-8b-instruct-fp8` | 5 | 10/10 | 16.15 | 18.98 | 81.5 |
| `@cf/meta/llama-3.1-8b-instruct-fp8` | 10 | 20/20 | 16.86 | 25.06 | 128.9 |
| `@cf/google/gemma-4-26b-a4b-it` | 1 | 20/20 | 18.08 | 40.45 | 45.7 |
| `@cf/google/gemma-4-26b-a4b-it` | 2 | 4/4 | 14.92 | 24.72 | 88.4 |
| `@cf/google/gemma-4-26b-a4b-it` | 5 | 10/10 | 15.24 | 19.05 | 227.0 |
| `@cf/google/gemma-4-26b-a4b-it` | 10 | 20/20 | 17.36 | 23.72 | 374.0 |
| `@cf/qwen/qwen3-30b-a3b-fp8` | 1 | 20/20 | 7.29 | 8.69 | 94.3 |
| `@cf/qwen/qwen3-30b-a3b-fp8` | 2 | 4/4 | 8.16 | 8.85 | 154.8 |
| `@cf/qwen/qwen3-30b-a3b-fp8` | 5 | 10/10 | 7.76 | 8.50 | 388.0 |
| `@cf/qwen/qwen3-30b-a3b-fp8` | 10 | 20/20 | 8.02 | 9.11 | 739.6 |

### 6.4 Ghi chú

- Không có request lỗi ở cả 5 model (cột Lỗi = 0); mọi model đều đạt 20/20 request warm thành công và đủ request thành công ở các mức đồng thời 2, 5, 10.
- Chỉ `@cf/google/gemma-4-26b-a4b-it` có request bị cắt do chạm giới hạn token: 8 request trên tổng 55 request của model này. Bốn model còn lại bị cắt 0 request.
- Thứ tự p50 warm từ thấp đến cao: `@cf/meta/llama-3.2-3b-instruct` (1.02 s), `@cf/meta/llama-3.2-1b-instruct` (2.25 s), `@cf/qwen/qwen3-30b-a3b-fp8` (7.29 s), `@cf/meta/llama-3.1-8b-instruct-fp8` (16.15 s), `@cf/google/gemma-4-26b-a4b-it` (18.08 s).
- p50 ở mức 10 đồng thời so với p50 ở mức 1 (tuần tự): llama-3.2-1b gấp khoảng 1,03 lần (2.32 / 2.25); llama-3.2-3b khoảng 1,01 lần (1.03 / 1.02); llama-3.1-8b-fp8 khoảng 1,04 lần (16.86 / 16.15); gemma-4-26b khoảng 0,96 lần (17.36 / 18.08); qwen3-30b khoảng 1,10 lần (8.02 / 7.29).
- Cold start so với p50 warm: llama-3.2-1b (4.377 s so với 2.25 s) và llama-3.1-8b-fp8 (24.693 s so với 16.15 s) lớn hơn p50 warm, lần lượt khoảng 1,9 lần và 1,5 lần; llama-3.2-3b (1.074 s so với 1.02 s), gemma-4-26b (17.533 s so với 18.08 s) và qwen3-30b (7.478 s so với 7.29 s) xấp xỉ p50 warm.
- Throughput tổng tăng khi tăng mức đồng thời từ 1 lên 10 ở cả 5 model, ví dụ llama-3.2-3b từ 149.6 lên 809.1 token/s và llama-3.1-8b-fp8 từ 17.0 lên 128.9 token/s.

### 6.5 Giới hạn của phép đo

- Mỗi model chỉ có 1 mẫu cold; p95 tính trên 20 mẫu nên chỉ là giá trị lớn thứ hai; mỗi mức đồng thời chỉ có 2 đợt.
- Đo từ máy phát triển qua Internet, không phải từ AI Service trên Vercel; số tuyệt đối sẽ khác khi đo từ hạ tầng thật (kế hoạch đo lại ở Tuần 17).
- Cloudflare Workers AI là nền tảng dùng chung nên latency phụ thuộc tải của Cloudflare tại thời điểm chạy; mới có một lần chạy ở một thời điểm.
- Không dùng streaming nên không có time-to-first-token.
- Với model có reasoning, latency và token/s bao gồm cả thời gian và token suy luận.