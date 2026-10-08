package com.example.lori.util;

/**
 * Quy doi so cau dung (raw, tren 40 cau) sang band IELTS Listening / Reading Academic, va tinh band tong.
 * Bang quy doi la bang pho bien (IELTS khong cong bo bang chinh thuc cho tung de), tinh theo raw >= nguong.
 * Band tong o day chi gom Listening + Reading vi Writing & Speaking chua co (Coming Soon).
 */
public final class IeltsScoreUtil {

    private static final int QUESTIONS_PER_SKILL = 40;

    // Cung vi tri: nguong raw toi thieu va band tuong ung (raw duoi nguong cuoi cung thi band 0.0)
    private static final double[] BANDS = {9.0, 8.5, 8.0, 7.5, 7.0, 6.5, 6.0, 5.5, 5.0, 4.5, 4.0, 3.5, 3.0, 2.5, 2.0};
    private static final int[] LISTENING_MIN_RAW = {39, 37, 35, 32, 30, 26, 23, 18, 16, 13, 10, 8, 6, 4, 1};
    private static final int[] READING_MIN_RAW = {39, 37, 35, 33, 30, 27, 23, 19, 15, 13, 10, 8, 6, 4, 1};

    private IeltsScoreUtil() {
    }

    public static double listeningBand(int correct, int total) {
        return band(LISTENING_MIN_RAW, correct, total);
    }

    public static double readingBand(int correct, int total) {
        return band(READING_MIN_RAW, correct, total);
    }

    /** Trung binh hai band, lam tron den 0.5 (0.25 -> 0.5, 0.75 -> len so nguyen tiep theo). */
    public static double overallBand(double listeningBand, double readingBand) {
        return Math.round(listeningBand + readingBand) / 2.0;
    }

    // total khac 40 (de nhap thieu/thua cau) thi quy ve thang 40 truoc khi tra bang
    private static double band(int[] minRawThresholds, int correct, int total) {
        if (total <= 0) {
            return 0.0;
        }
        int safeCorrect = Math.max(0, Math.min(correct, total));
        int raw = total == QUESTIONS_PER_SKILL
                ? safeCorrect
                : (int) Math.round(safeCorrect * (double) QUESTIONS_PER_SKILL / total);
        for (int i = 0; i < minRawThresholds.length; i++) {
            if (raw >= minRawThresholds[i]) {
                return BANDS[i];
            }
        }
        return 0.0;
    }
}