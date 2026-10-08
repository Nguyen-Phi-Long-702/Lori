package com.example.lori.util;

/**
 * Quy doi so cau dung (raw) sang diem TOEIC (scaled).
 * Moi ky nang (Listening / Reading) co diem 5-495, chia het cho 5; tong hai ky nang 10-990.
 * ETS khong cong bo bang quy doi chinh thuc (moi de mot bang rieng) nen day la cong thuc tuyen tinh gan dung:
 * diem = 5 + (ti le cau dung) x 490, lam tron den boi so cua 5.
 */
public final class ToeicScoreUtil {

    private static final int MIN_SECTION_SCORE = 5;
    private static final int MAX_SECTION_SCORE = 495;
    private static final int STEP = 5;

    private ToeicScoreUtil() {
    }

    /** Diem cua MOT ky nang. total = so cau cua ky nang trong de; total <= 0 thi tra 0. */
    public static int sectionScore(int correct, int total) {
        if (total <= 0) {
            return 0;
        }
        int safeCorrect = Math.max(0, Math.min(correct, total));
        double raw = MIN_SECTION_SCORE
                + (double) safeCorrect / total * (MAX_SECTION_SCORE - MIN_SECTION_SCORE);
        return (int) (Math.round(raw / STEP) * STEP);
    }
}