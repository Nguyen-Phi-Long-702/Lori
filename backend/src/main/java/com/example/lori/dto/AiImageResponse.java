package com.example.lori.dto;

import java.util.List;

/**
 * Ket qua nhan dien anh: type = "image_identification", objects theo do tin cay giam dan (toi da 5, moi tu mot vat,
 * rong neu khong nhan ra vat nao). Cung la dang JSON ma AI Service tra ve cho Backend.
 */
public record AiImageResponse(String type, List<ImageObject> objects) {

    /**
     * Mot vat. word la ten tieng Anh cua vat; ipa lay tu dictionary.db cua Lori (null neu tu dien khong co);
     * meaning la nghia tieng Viet cua do vat; confidence 0..1; box la hinh chu nhat bao vat.
     * example / exampleVi (cau vi du tieng Anh va ban dich) do AI sinh, null khi AI khong tao duoc.
     */
    public record ImageObject(
            String word, String ipa, String meaning, double confidence, Box box, String example, String exampleVi) {
    }

    /** Toa do chuan hoa 0..1 theo anh goc: goc tren-trai (x, y) va kich thuoc (width, height). */
    public record Box(double x, double y, double width, double height) {
    }
}