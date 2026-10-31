import ast
import io
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np
import onnxruntime
from PIL import Image, ImageOps

# yolov8n.pt (pretrained) xuat sang ONNX, xem INFRA.md muc 26
MODEL_PATH = Path(__file__).resolve().parent.parent / "data" / "yolov8n.onnx"
IMAGE_SIZE = 640
# Hai nguong mac dinh cua ultralytics predict
CONFIDENCE_THRESHOLD = 0.25
IOU_THRESHOLD = 0.7
PAD_VALUE = 114
# Backend da gioi han 3 MB; kiem tra lai vi AI Service la ranh gioi tin cay rieng
MAX_IMAGE_BYTES = 3 * 1024 * 1024
MAX_PIXELS = 40_000_000
ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP"}


class InvalidImageError(Exception):
    """Du lieu khong phai anh JPEG/PNG/WEBP doc duoc, hoac anh qua lon."""


@dataclass(frozen=True)
class Detection:
    label: str
    confidence: float
    # Toa do chuan hoa 0..1 theo anh goc (sau khi xoay theo EXIF)
    x: float
    y: float
    width: float
    height: float


@lru_cache(maxsize=1)
def _load_model() -> tuple[onnxruntime.InferenceSession, dict[int, str]]:
    session = onnxruntime.InferenceSession(str(MODEL_PATH), providers=["CPUExecutionProvider"])
    # ultralytics ghi ten 80 lop vao metadata cua file ONNX
    names = ast.literal_eval(session.get_modelmeta().custom_metadata_map["names"])
    return session, names


def class_names() -> list[str]:
    return list(_load_model()[1].values())


def detect_objects(data: bytes) -> list[Detection]:
    """Nhan dien vat the trong anh bang YOLOv8n, tra ve theo do tin cay giam dan."""
    image = _decode(data)
    session, names = _load_model()
    tensor, ratio, pad_x, pad_y = _letterbox(image)
    output = session.run(None, {session.get_inputs()[0].name: tensor})[0]
    return _postprocess(output, names, image.size, ratio, pad_x, pad_y)


def _decode(data: bytes) -> Image.Image:
    if len(data) > MAX_IMAGE_BYTES:
        raise InvalidImageError("Anh qua lon")
    try:
        image = Image.open(io.BytesIO(data))
        if image.format not in ALLOWED_FORMATS or image.width * image.height > MAX_PIXELS:
            raise InvalidImageError("Dinh dang hoac kich thuoc anh khong duoc ho tro")
        image.load()
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError):
        raise InvalidImageError("Khong doc duoc anh") from None
    # Anh chup bang dien thoai co the nam ngang trong file va chi xoay nho the EXIF
    return ImageOps.exif_transpose(image).convert("RGB")


def _letterbox(image: Image.Image) -> tuple[np.ndarray, float, float, float]:
    """Thu nho giu ty le vao khung 640x640, phan thua dem mau xam (giong ultralytics)."""
    width, height = image.size
    ratio = min(IMAGE_SIZE / width, IMAGE_SIZE / height)
    new_width, new_height = round(width * ratio), round(height * ratio)
    pad_x, pad_y = (IMAGE_SIZE - new_width) / 2, (IMAGE_SIZE - new_height) / 2
    canvas = Image.new("RGB", (IMAGE_SIZE, IMAGE_SIZE), (PAD_VALUE,) * 3)
    canvas.paste(image.resize((new_width, new_height), Image.BILINEAR), (round(pad_x - 0.1), round(pad_y - 0.1)))
    tensor = np.asarray(canvas, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0
    return tensor, ratio, pad_x, pad_y


def _postprocess(
    output: np.ndarray, names: dict[int, str], size: tuple[int, int], ratio: float, pad_x: float, pad_y: float
) -> list[Detection]:
    # output (1, 84, 8400): 4 so dau la tam x, tam y, rong, cao (pixel trong khung 640), 80 so sau la diem tung lop
    rows = output[0].T
    scores = rows[:, 4:]
    class_ids = scores.argmax(axis=1)
    confidences = scores.max(axis=1)
    keep = confidences >= CONFIDENCE_THRESHOLD
    rows, class_ids, confidences = rows[keep], class_ids[keep], confidences[keep]

    centers, half = rows[:, :2], rows[:, 2:4] / 2
    boxes = np.concatenate([centers - half, centers + half], axis=1)
    # Moi lop NMS rieng: doi toa do theo lop de hai lop khac nhau khong bao gio chong len nhau
    kept = _nms(boxes + class_ids[:, None] * IMAGE_SIZE * 2, confidences)

    width, height = size
    detections = []
    for i in kept:
        # Bo phan dem va phong ve kich thuoc anh goc, roi cat cho khong vuot ra ngoai anh
        x1, y1, x2, y2 = (boxes[i] - [pad_x, pad_y, pad_x, pad_y]) / ratio
        x1, x2 = np.clip([x1, x2], 0, width)
        y1, y2 = np.clip([y1, y2], 0, height)
        detections.append(
            Detection(
                label=names[int(class_ids[i])],
                confidence=round(float(confidences[i]), 4),
                x=round(float(x1 / width), 4),
                y=round(float(y1 / height), 4),
                width=round(float((x2 - x1) / width), 4),
                height=round(float((y2 - y1) / height), 4),
            )
        )
    return detections


def _nms(boxes: np.ndarray, scores: np.ndarray) -> list[int]:
    order = scores.argsort()[::-1]
    kept = []
    while order.size:
        i, rest = order[0], order[1:]
        kept.append(int(i))
        inter_w = np.minimum(boxes[i, 2], boxes[rest, 2]) - np.maximum(boxes[i, 0], boxes[rest, 0])
        inter_h = np.minimum(boxes[i, 3], boxes[rest, 3]) - np.maximum(boxes[i, 1], boxes[rest, 1])
        inter = np.clip(inter_w, 0, None) * np.clip(inter_h, 0, None)
        area_i = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
        areas = (boxes[rest, 2] - boxes[rest, 0]) * (boxes[rest, 3] - boxes[rest, 1])
        order = rest[inter / (area_i + areas - inter) <= IOU_THRESHOLD]
    return kept