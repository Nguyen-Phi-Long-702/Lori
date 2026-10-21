package com.example.lori.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceSignerTest {

    private static final String SECRET = "lori-test-shared-secret-0123456789abcdef";
    private static final byte[] BODY = "{\"hello\":\"lori\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void signMatchesSharedTestVector() {
        // Gia tri nay phai trung voi test_s2s_auth.py ben AI Service
        String signature = AiServiceSigner.sign(SECRET, "1760000000", "POST", "/ai/ping", BODY);
        assertThat(signature).isEqualTo("a2ccda8db585a00eebc97d2b0ae19b15afc9b2b1f3b2530db1b36237efe89a3a");
    }

    @Test
    void changedBodyChangesSignature() {
        byte[] otherBody = "{\"hello\":\"evil\"}".getBytes(StandardCharsets.UTF_8);
        String original = AiServiceSigner.sign(SECRET, "1760000000", "POST", "/ai/ping", BODY);
        String tampered = AiServiceSigner.sign(SECRET, "1760000000", "POST", "/ai/ping", otherBody);
        assertThat(tampered).isNotEqualTo(original);
    }
}