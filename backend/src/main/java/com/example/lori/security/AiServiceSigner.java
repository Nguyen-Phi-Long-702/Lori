package com.example.lori.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Ky request Backend -> AI Service bang HMAC-SHA256
 * Chuoi duoc ky: timestamp \n METHOD \n path \n sha256-hex(body). AI Service tinh lai theo dung cong thuc nay.
 */
public final class AiServiceSigner {

    public static final String TIMESTAMP_HEADER = "X-Lori-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Lori-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private AiServiceSigner() {
    }

    public static String sign(String secret, String timestamp, String method, String path, byte[] body) {
        String canonical = timestamp + "\n" + method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Khong tinh duoc HMAC-SHA256", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Khong tinh duoc SHA-256", e);
        }
    }
}