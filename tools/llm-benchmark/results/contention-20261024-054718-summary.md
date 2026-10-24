- Phien ban: ultralytics 8.4.185, torch 2.14.1+cpu, so luong thread torch 3
- Model LLM: @cf/qwen/qwen3-30b-a3b-fp8; model YOLO: yolov8n.pt, anh bus.jpg, imgsz 640, CPU
- Import thu vien va nap YOLO: 4.09s; suy luan YOLO lan dau (da gom khoi tao): 3.41s

| Loai | Pha | Thanh cong | p50 (s) | p95 (s) | Trung binh (s) |
|---|---|---|---|---|---|
| llm | alone | 10/10 | 6.70 | 8.49 | 6.32 |
| llm | together | 10/10 | 5.10 | 7.96 | 5.33 |
| yolo | alone | 20/20 | 0.40 | 0.48 | 0.41 |
| yolo | together | 176/176 | 0.30 | 0.37 | 0.30 |

| Moc do RAM dinh (ru_maxrss) | MB |
|---|---|
| Sau pha LLM mot minh (chua import ultralytics/torch) | 28 |
| Sau khi nap YOLO va suy luan lan dau | 417 |
| Sau pha YOLO mot minh | 432 |
| Sau pha YOLO + LLM dong thoi | 457 |
