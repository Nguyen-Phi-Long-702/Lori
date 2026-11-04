"""Kiem thu Image Learning qua duong that Backend (Render) -> AI Service (Vercel) -> YOLOv8n + Cloudflare Workers AI
(ke hoach Tuan 14, Ngay 7). Cach chay: xem INFRA.md muc 28.

Anh mau nam trong tools/ai-test/images/ (khong commit), ten anh va cac vat ky vong nam trong image_cases.json.
Phan tu dong chi kiem tra cau truc va viec nhan ra du cac vat da ghi trong "expect"; vat thua, IPA, nghia va cau vi du
do nguoi doc trong file ket qua results/image-learning-<ngay>-<gio UTC>.md.
"""
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import ai_test_common as api

HERE = Path(__file__).parent
IMAGES = HERE / "images"
# AiRateLimiter cho identify-image 10 request/phut/user (nap lai 1 luot moi 6 giay); nghi 6 giay giua 2 anh de khong cham gioi han
DELAY_SECONDS = 6
MAX_IMAGE_BYTES = 3 * 1024 * 1024
MAX_OBJECTS = 5


def load_cases():
    cases = json.loads((HERE / "image_cases.json").read_text(encoding="utf-8"))
    for case in cases:
        path = IMAGES / case["file"]
        if not path.is_file():
            sys.exit(f"Khong thay anh {path}")
        if path.stat().st_size > MAX_IMAGE_BYTES:
            sys.exit(f"{case['file']} lon hon 3 MB, hay thu nho anh roi chay lai")
    return cases


def check_object(item):
    name = item.get("word") or "?"
    problems = []
    if not (item.get("meaning") or "").strip():
        problems.append(f"{name}: meaning rong")
    confidence = item.get("confidence")
    if not (isinstance(confidence, (int, float)) and 0 < confidence <= 1):
        problems.append(f"{name}: confidence = {confidence}")
    box = item.get("box") or {}
    try:
        x, y, width, height = (box[key] for key in ("x", "y", "width", "height"))
        inside = x >= 0 and y >= 0 and width > 0 and height > 0 and x + width <= 1.001 and y + height <= 1.001
    except (KeyError, TypeError):
        inside = False
    if not inside:
        problems.append(f"{name}: box khong nam trong anh: {box}")
    for key in ("example", "exampleVi"):
        if not (item.get(key) or "").strip():
            problems.append(f"{name}: {key} rong (AI khong tao duoc cau vi du)")
    return problems


def check(case, status, body):
    if status != 200:
        return [f"HTTP {status}, ky vong 200"]
    problems = []
    if body.get("type") != "image_identification":
        problems.append(f"type = {body.get('type')}")
    objects = body.get("objects")
    if not isinstance(objects, list):
        return problems + ["objects khong phai danh sach"]
    words = [item.get("word") for item in objects]
    if len(objects) > MAX_OBJECTS:
        problems.append(f"{len(objects)} vat, toi da {MAX_OBJECTS}")
    if len(set(words)) != len(words):
        problems.append("co tu bi lap")
    values = [item["confidence"] for item in objects if isinstance(item.get("confidence"), (int, float))]
    if values != sorted(values, reverse=True):
        problems.append("objects khong sap theo confidence giam dan")
    for item in objects:
        problems += check_object(item)
    missing = [word for word in case["expect"] if word not in words]
    if missing:
        problems.append(f"khong nhan ra: {', '.join(missing)}")
    if not case["expect"] and objects:
        problems.append(f"anh khong co vat nao trong 80 lop nhung nhan ra: {', '.join(map(str, words))}")
    return problems


def describe(body):
    objects = body.get("objects") if isinstance(body.get("objects"), list) else []
    return ", ".join(f"{item.get('word')} {round((item.get('confidence') or 0) * 100)}%" for item in objects)


def extra_words(case, body):
    objects = body.get("objects") if isinstance(body.get("objects"), list) else []
    return ", ".join(str(item.get("word")) for item in objects if item.get("word") not in case["expect"])


def write_report(path, started, setup_lines, results):
    passed = sum(1 for item in results if not item["problems"])
    expected = sum(len(item["case"]["expect"]) for item in results)
    found = sum(1 for item in results for word in item["case"]["expect"]
                if word in [o.get("word") for o in (item["body"].get("objects") or []) if isinstance(o, dict)])
    average = sum(item["elapsed"] for item in results) / len(results)
    lines = [
        "# Ket qua kiem thu Image Learning (Tuan 14, Ngay 7)",
        "",
        f"- Bat dau: {started:%Y-%m-%d %H:%M:%S} UTC",
        f"- Backend: {api.BASE_URL}",
        *[f"- {line}" for line in setup_lines],
        f"- Tu dong: {passed}/{len(results)} anh dat",
        f"- Vat ky vong nhan ra duoc: {found}/{expected}",
        f"- Thoi gian trung binh moi anh: {average:.1f} s",
        "",
        "| File | HTTP | Thoi gian (s) | Tu dong | Ky vong | Nhan duoc | Thua (can doi chieu voi anh) |",
        "|---|---|---|---|---|---|---|",
    ]
    for item in results:
        case, body = item["case"], item["body"]
        lines.append(f"| {case['file']} | {item['status']} | {item['elapsed']:.1f} "
                     f"| {'Dat' if not item['problems'] else 'KHONG DAT'} "
                     f"| {', '.join(case['expect']) or '(khong co)'} | {describe(body)} | {extra_words(case, body)} |")
    for item in results:
        lines += ["", f"## {item['case']['file']}", ""]
        if item["problems"]:
            lines += ["Khong dat:", *[f"- {problem}" for problem in item["problems"]], ""]
        lines += ["```json", json.dumps(item["body"], ensure_ascii=False, indent=2), "```"]
    path.parent.mkdir(exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    started = datetime.now(timezone.utc)
    cases = load_cases()
    email, token = api.register_user("image")
    setup_lines = [f"User thu: {email} (Free, sau do mua Premium bang mock-purchase)"]

    probe = cases[0]["file"]
    status, body, _ = api.post_image("/api/ai/identify-image", probe, (IMAGES / probe).read_bytes(), token)
    gate_ok = api.is_premium_blocked(status, body)
    print(f"[{'PASS' if gate_ok else 'FAIL'}] GATE user Free bi chan 403 PREMIUM_REQUIRED (HTTP {status})")
    setup_lines.append(f"Premium gate (user Free): {'Dat' if gate_ok else 'KHONG DAT, HTTP ' + str(status)}")

    api.buy_premium(token)

    results = []
    for case in cases:
        status, body, elapsed = api.post_image(
            "/api/ai/identify-image", case["file"], (IMAGES / case["file"]).read_bytes(), token)
        problems = check(case, status, body)
        results.append({"case": case, "status": status, "body": body, "elapsed": elapsed, "problems": problems})
        print(f"[{'PASS' if not problems else 'FAIL'}] {case['file']} HTTP {status} {elapsed:.1f}s: {describe(body)}"
              + "".join(f"\n       - {problem}" for problem in problems))
        time.sleep(DELAY_SECONDS)

    report = HERE / "results" / f"image-learning-{started:%Y%m%d-%H%M%S}.md"
    write_report(report, started, setup_lines, results)
    failed = sum(1 for item in results if item["problems"])
    print(f"\nTu dong: {len(results) - failed}/{len(results)} anh dat. File ket qua: {report}")
    sys.exit(1 if failed or not gate_ok else 0)


if __name__ == "__main__":
    api.require_env()
    main()