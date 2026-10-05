package com.example.lori.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Lop anti-abuse: moi request (tru SecurityPaths.OPEN_PATHS) phai co dung X-API-Key va X-App-Signature.
 * Khong phai ranh gioi bao mat that (ke hoach muc 8.0): quyen truy cap van do JWT quyet dinh.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String APP_SIGNATURE_HEADER = "X-App-Signature";

    private final byte[] expectedApiKey;
    private final byte[] expectedAppSignature;

    public ApiKeyFilter(String apiKey, String appSignature) {
        this.expectedApiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.expectedAppSignature = appSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.isOpenPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        String appSignature = request.getHeader(APP_SIGNATURE_HEADER);

        if (apiKey != null && appSignature != null
                && sameBytes(expectedApiKey, apiKey)
                && sameBytes(expectedAppSignature, appSignature.toLowerCase(Locale.ROOT))) {
            chain.doFilter(request, response);
            return;
        }
        JsonErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "Invalid API key or app signature");
    }

    // So sanh thoi gian khong doi, tranh lo thong tin qua do tre
    private static boolean sameBytes(byte[] expected, String actual) {
        return MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8));
    }
}