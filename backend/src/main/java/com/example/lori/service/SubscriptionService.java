package com.example.lori.service;

import com.example.lori.entity.PlanType;
import com.example.lori.entity.Subscription;
import com.example.lori.entity.User;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.SubscriptionRepository;
import com.example.lori.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Quan ly goi Premium, doc lap voi cach thanh toan (Mock hom nay; Google/VNPay/Momo sau nay chi can goi activate).
 * Quyen Premium thuc su do users.is_premium + users.premium_expires_at (PremiumAspect doc hai cot nay);
 * bang subscriptions la lich su goi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    // Phai khop voi chuoi trong truy van o SubscriptionRepository
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Kich hoat goi cho user: tao 1 dong subscriptions (ACTIVE) va cap nhat is_premium / premium_expires_at.
     * - Goi co han mua khi dang con Premium co han: noi tiep tu luc het han hien tai.
     * - Dang Premium vinh vien: 409.
     * - Con lai (Free / da het han / mua goi vinh vien): tinh tu bay gio.
     */
    @Transactional
    public Subscription activate(UUID userId, PlanType planType, String paymentMethod, String transactionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        Instant now = Instant.now();
        Instant currentExpiry = user.getPremiumExpiresAt();
        boolean premiumActive = user.isPremium() && (currentExpiry == null || currentExpiry.isAfter(now));
        if (premiumActive && currentExpiry == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Already have lifetime Premium");
        }

        Instant startsAt = (premiumActive && !planType.isLifetime()) ? currentExpiry : now;
        Instant expiresAt = planType.isLifetime() ? null : startsAt.plus(Duration.ofDays(planType.getDays()));

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanType(planType.name());
        subscription.setPaymentMethod(paymentMethod);
        subscription.setTransactionId(transactionId);
        subscription.setStatus(STATUS_ACTIVE);
        subscription.setStartsAt(startsAt);
        subscription.setExpiresAt(expiresAt);
        Subscription saved = subscriptionRepository.save(subscription);

        user.setPremium(true);
        user.setPremiumExpiresAt(expiresAt);

        log.info("Kich hoat Premium: userId={}, plan={}, method={}", userId, planType, paymentMethod);
        return saved;
    }

    /** Dung cho SubscriptionExpiryScheduler: danh dau goi het han va ha quyen user het han trong 1 transaction. */
    @Transactional
    public void expireDueSubscriptions() {
        Instant now = Instant.now();
        int subscriptions = subscriptionRepository.expireDue(now);
        int users = userRepository.downgradeExpiredPremium(now);
        if (subscriptions > 0 || users > 0) {
            log.info("Het han Premium: {} subscription, {} user", subscriptions, users);
        }
    }
}