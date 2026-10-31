import io

import numpy as np
import pytest
from PIL import Image

from providers import yolo_detector
from providers.yolo_detector import InvalidImageError, _decode, _letterbox, _postprocess

NAMES = {0: "person", 5: "bus"}


def image_bytes(size=(64, 32), fmt="PNG", **save_args):
    buffer = io.BytesIO()
    Image.new("RGB", size, (200, 30, 30)).save(buffer, format=fmt, **save_args)
    return buffer.getvalue()


def make_output(*anchors):
    # anchors: (tam x, tam y, rong, cao, so lop, diem); moi anchor la mot cot trong tensor (1, 84, 8400)
    output = np.zeros((1, 84, 8400), dtype=np.float32)
    for column, (cx, cy, w, h, class_id, score) in enumerate(anchors):
        output[0, :4, column] = (cx, cy, w, h)
        output[0, 4 + class_id, column] = score
    return output


def test_letterbox_keeps_ratio_and_pads_to_640():
    tensor, ratio, pad_x, pad_y = _letterbox(Image.new("RGB", (1280, 640)))
    assert tensor.shape == (1, 3, 640, 640)
    assert (ratio, pad_x, pad_y) == (0.5, 0, 160)
    assert tensor[0, 0, 0, 0] == pytest.approx(114 / 255)  # dai tren: mau xam
    assert tensor[0, 0, 320, 320] == 0  # giua: anh den


def test_postprocess_maps_boxes_back_to_the_original_image():
    # Anh goc 1280x640 -> ratio 0.5, pad_y 160; vat o giua khung 640 chiem 320x160 pixel
    output = make_output((320, 320, 320, 160, 0, 0.9))
    [detection] = _postprocess(output, NAMES, (1280, 640), 0.5, 0, 160)
    assert (detection.label, detection.confidence) == ("person", 0.9)
    assert (detection.x, detection.y, detection.width, detection.height) == (0.25, 0.25, 0.5, 0.5)


def test_postprocess_drops_low_confidence_and_duplicate_boxes_of_the_same_class():
    output = make_output(
        (320, 320, 200, 200, 0, 0.9),
        (322, 322, 200, 200, 0, 0.6),  # trung voi hop tren -> bi NMS loai
        (100, 100, 50, 50, 0, 0.1),  # duoi nguong 0.25
    )
    detections = _postprocess(output, NAMES, (640, 640), 1, 0, 0)
    assert [d.confidence for d in detections] == [0.9]


def test_postprocess_keeps_overlapping_boxes_of_different_classes():
    output = make_output((320, 320, 200, 200, 0, 0.9), (320, 320, 200, 200, 5, 0.8))
    detections = _postprocess(output, NAMES, (640, 640), 1, 0, 0)
    assert [d.label for d in detections] == ["person", "bus"]


def test_postprocess_clips_boxes_to_the_image():
    output = make_output((630, 320, 100, 100, 0, 0.9))
    [detection] = _postprocess(output, NAMES, (640, 640), 1, 0, 0)
    assert detection.x + detection.width == 1.0


@pytest.mark.parametrize("data", [b"", b"not an image", image_bytes()[:20]])
def test_decode_rejects_data_that_is_not_a_readable_image(data):
    with pytest.raises(InvalidImageError):
        _decode(data)


def test_decode_rejects_unsupported_format():
    with pytest.raises(InvalidImageError):
        _decode(image_bytes(fmt="GIF"))


def test_decode_rejects_oversized_data(monkeypatch):
    monkeypatch.setattr(yolo_detector, "MAX_IMAGE_BYTES", 10)
    with pytest.raises(InvalidImageError):
        _decode(image_bytes())


def test_decode_rejects_image_with_too_many_pixels(monkeypatch):
    monkeypatch.setattr(yolo_detector, "MAX_PIXELS", 100)
    with pytest.raises(InvalidImageError):
        _decode(image_bytes())


def test_decode_rotates_by_exif_orientation():
    exif = Image.Exif()
    exif[0x0112] = 6  # xoay 90 do khi hien thi
    image = _decode(image_bytes(fmt="JPEG", exif=exif))
    assert image.size == (32, 64)


def test_real_model_loads_80_classes_and_runs_on_a_blank_image():
    names = yolo_detector.class_names()
    assert len(names) == 80
    assert {"person", "bus", "hair drier"} <= set(names)
    assert yolo_detector.detect_objects(image_bytes(size=(320, 240))) == []