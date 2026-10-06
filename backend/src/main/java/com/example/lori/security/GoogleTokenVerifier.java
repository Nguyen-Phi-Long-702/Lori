package com.example.lori.security;

import com.example.lori.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Xac thuc Google ID Token: chu ky (khoa cong khai cua Google), het han, iss, aud = Web client ID, email_verified.
 * NimbusJwtDecoder chi tai khoa cua Google khi can (lan giai ma dau tien) nen khoi dong app khong can mang.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    // Google dung mot trong hai dang issuer nay
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private final JwtDecoder jwtDecoder;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        this.clientId = clientId;
        // Mac dinh NimbusJwtDecoder da kiem tra chu ky va thoi han (exp/nbf)
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
    }

    /** Thong tin lay tu ID Token da xac thuc. name va pictureUrl co the null. */
    public record GoogleUserInfo(String googleId, String email, String name, String pictureUrl) {
    }

    /** Token khong hop le (bat ky ly do nao) -> 401 "Invalid Google ID token". */
    public GoogleUserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            log.warn("Google ID Token khong hop le: {}", e.getMessage());
            throw invalidToken();
        }

        String issuer = jwt.getClaimAsString("iss");
        List<String> audience = jwt.getAudience();
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));

        boolean valid = issuer != null && GOOGLE_ISSUERS.contains(issuer)
                && audience != null && audience.contains(clientId)
                && emailVerified
                && email != null && !email.isBlank()
                && jwt.getSubject() != null;
        if (!valid) {
            log.warn("Google ID Token bi tu choi: iss={}, aud={}, email_verified={}", issuer, audience, emailVerified);
            throw invalidToken();
        }
        return new GoogleUserInfo(jwt.getSubject(), email, jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
    }
}