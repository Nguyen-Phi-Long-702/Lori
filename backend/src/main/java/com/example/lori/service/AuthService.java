package com.example.lori.service;

import com.example.lori.dto.AuthResponse;
import com.example.lori.dto.LoginRequest;
import com.example.lori.dto.RegisterRequest;
import com.example.lori.entity.RefreshToken;
import com.example.lori.entity.User;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.RefreshTokenRepository;
import com.example.lori.repository.UserRepository;
import com.example.lori.security.GoogleTokenVerifier;
import com.example.lori.security.GoogleTokenVerifier.GoogleUserInfo;
import com.example.lori.security.JwtTokenProvider;
import com.example.lori.util.InputSanitizerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** Nghiep vu dang ky / dang nhap (email, Google) / lam moi token / dang xuat. */
@Service
@RequiredArgsConstructor
public class AuthService {

    // Gioi han do dai theo cot trong V1__init_schema.sql (users.display_name, users.avatar_url)
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_AVATAR_URL_LENGTH = 500;
    private static final String DEFAULT_DISPLAY_NAME = "Lori User";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final BruteForceProtectionService bruteForceProtectionService;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        String displayName = InputSanitizerUtil.sanitize(request.displayName());
        if (displayName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Display name is invalid");
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (bruteForceProtectionService.isLocked(email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts, try again later");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        // Cung mot thong bao cho "khong co email" va "sai mat khau" de khong lo email nao ton tai
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            bruteForceProtectionService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        bruteForceProtectionService.reset(email);
        return issueTokens(user);
    }

    /**
     * Dang nhap Google: xac thuc ID Token, tim user theo google_id; chua co thi tim theo email de lien ket
     * (account linking); van chua co thi tao user moi (khong co mat khau).
     */
    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleUserInfo info = googleTokenVerifier.verify(idToken);
        String email = normalizeEmail(info.email());

        User user = userRepository.findByGoogleId(info.googleId()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setDisplayName(resolveDisplayName(info.name(), email));
            }
            // Email da dang ky bang mat khau thi giu nguyen tai khoan, chi gan them google_id (account linking)
            user.setGoogleId(info.googleId());
            if (user.getAvatarUrl() == null) {
                user.setAvatarUrl(resolveAvatarUrl(info.pictureUrl()));
            }
            user = userRepository.save(user);
        }
        return issueTokens(user);
    }

    /** Xoay vong: thu hoi refresh token cu, cap token moi. */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        stored.setRevoked(true);
        return issueTokens(user);
    }

    /** Thu hoi refresh token; token khong ton tai thi bo qua (idempotent). */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> token.setRevoked(true));
    }

    private AuthResponse issueTokens(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(jwtTokenProvider.generateRefreshTokenValue());
        refreshToken.setExpiresAt(Instant.now().plus(jwtTokenProvider.getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                jwtTokenProvider.generateAccessToken(user.getId()),
                refreshToken.getToken());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Ten Google -> lam sach, cat toi da 100 ky tu; trong thi dung phan truoc @ cua email. */
    private static String resolveDisplayName(String googleName, String email) {
        String name = InputSanitizerUtil.sanitize(googleName);
        if (name == null || name.isEmpty()) {
            name = InputSanitizerUtil.sanitize(email.split("@")[0]);
        }
        if (name == null || name.isEmpty()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return name.length() > MAX_DISPLAY_NAME_LENGTH ? name.substring(0, MAX_DISPLAY_NAME_LENGTH) : name;
    }

    /** URL avatar dai hon cot (500) thi bo qua, de khong loi khi luu. */
    private static String resolveAvatarUrl(String pictureUrl) {
        if (pictureUrl == null || pictureUrl.isBlank() || pictureUrl.length() > MAX_AVATAR_URL_LENGTH) {
            return null;
        }
        return pictureUrl;
    }
}