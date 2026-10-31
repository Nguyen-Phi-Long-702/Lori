# Tu (ten lop COCO cua YOLOv8) co nghia dau tien trong dictionary.db khong phai nghia cua do vat
# (vi du "truck" -> "Su trao doi") hoac khong co trong tu dien: lay nghia o day thay vi tu dien
OBJECT_MEANINGS = {
    "truck": "xe tải",
    "motorcycle": "xe máy",
    "bear": "con gấu",
    "tie": "cà vạt",
    "clock": "đồng hồ",
    "toaster": "máy nướng bánh mì",
    "microwave": "lò vi sóng",
    "scissors": "cái kéo",
    "skis": "đôi ván trượt tuyết",
    "mouse": "chuột máy tính",
    "remote": "điều khiển từ xa",
    "keyboard": "bàn phím",
    "apple": "quả táo",
    "banana": "quả chuối",
    "carrot": "củ cà rốt",
    "broccoli": "bông cải xanh",
    "stop sign": "biển báo dừng",
    "parking meter": "đồng hồ đỗ xe",
    "snowboard": "ván trượt tuyết đơn",
    "sports ball": "quả bóng thể thao",
    "baseball bat": "gậy bóng chày",
    "baseball glove": "găng tay bóng chày",
    "tennis racket": "vợt tennis",
    "wine glass": "ly rượu vang",
    "potted plant": "chậu cây",
    "dining table": "bàn ăn",
    "teddy bear": "gấu bông",
    "hair dryer": "máy sấy tóc",
}

# Ten lop COCO viet sai chinh ta -> tu dung de hien thi va tra tu dien
OBJECT_WORDS = {"hair drier": "hair dryer"}