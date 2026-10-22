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