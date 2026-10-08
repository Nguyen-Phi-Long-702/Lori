package com.example.lori.security;

import com.example.lori.entity.User;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Chan user Free o moi ham gan @RequirePremium (tren ham hoac tren class).
 * Premium con hieu luc = is_premium = true VA (premium_expires_at rong = vinh vien, hoac con o tuong lai);
 * kiem tra ca han de dung ngay ca khi job ha quyen khi het han chua kip chay.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class PremiumAspect {

    /** Ma loi tra trong body {"status":403,"message":"PREMIUM_REQUIRED"} de app mo man hinh nang cap. */
    public static final String PREMIUM_REQUIRED = "PREMIUM_REQUIRED";

    private final UserRepository userRepository;

    @Before("@annotation(com.example.lori.security.RequirePremium)"
            + " || @within(com.example.lori.security.RequirePremium)")
    public void requirePremium() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        boolean premiumActive = user.isPremium()
                && (user.getPremiumExpiresAt() == null || user.getPremiumExpiresAt().isAfter(Instant.now()));
        if (!premiumActive) {
            throw new ApiException(HttpStatus.FORBIDDEN, PREMIUM_REQUIRED);
        }
    }
}