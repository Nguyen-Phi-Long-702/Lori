package com.example.lori.service;

import com.example.lori.entity.PlanType;
import com.example.lori.entity.Subscription;
import com.example.lori.entity.User;
import com.example.lori.repository.SubscriptionRepository;
import com.example.lori.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository);
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void saveReturnsTheSubscriptionItWasGiven() {
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void freeUserBuyingMonthlyGetsThirtyDaysFromNow() {
        User user = givenUser(false, null);
        Instant before = Instant.now();

        Subscription subscription = service.activate(userId, PlanType.MONTHLY, "MOCK", "MOCK-1");

        Instant after = Instant.now();
        assertThat(subscription.getUserId()).isEqualTo(userId);
        assertThat(subscription.getPlanType()).isEqualTo("MONTHLY");
        assertThat(subscription.getPaymentMethod()).isEqualTo("MOCK");
        assertThat(subscription.getTransactionId()).isEqualTo("MOCK-1");
        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
        assertThat(subscription.getStartsAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(subscription.getExpiresAt()).isEqualTo(subscription.getStartsAt().plus(Duration.ofDays(30)));
        assertThat(user.isPremium()).isTrue();
        assertThat(user.getPremiumExpiresAt()).isEqualTo(subscription.getExpiresAt());
    }

    @Test
    void freeUserBuyingYearlyGetsThreeHundredSixtyFiveDays() {
        givenUser(false, null);

        Subscription subscription = service.activate(userId, PlanType.YEARLY, "MOCK", "MOCK-1");

        assertThat(subscription.getExpiresAt()).isEqualTo(subscription.getStartsAt().plus(Duration.ofDays(365)));
    }

    @Test
    void lifetimePlanNeverExpires() {
        User user = givenUser(false, null);

        Subscription subscription = service.activate(userId, PlanType.LIFETIME, "MOCK", "MOCK-1");

        assertThat(subscription.getExpiresAt()).isNull();
        assertThat(user.isPremium()).isTrue();
        assertThat(user.getPremiumExpiresAt()).isNull();
    }

    @Test
    void renewingALimitedPlanContinuesFromTheCurrentExpiry() {
        Instant currentExpiry = Instant.now().plus(Duration.ofDays(10));
        User user = givenUser(true, currentExpiry);

        Subscription subscription = service.activate(userId, PlanType.MONTHLY, "MOCK", "MOCK-2");

        assertThat(subscription.getStartsAt()).isEqualTo(currentExpiry);
        assertThat(subscription.getExpiresAt()).isEqualTo(currentExpiry.plus(Duration.ofDays(30)));
        assertThat(user.getPremiumExpiresAt()).isEqualTo(subscription.getExpiresAt());
    }

    @Test
    void buyingLifetimeWhileLimitedPremiumStartsNowAndNeverExpires() {
        Instant currentExpiry = Instant.now().plus(Duration.ofDays(10));
        User user = givenUser(true, currentExpiry);
        Instant before = Instant.now();

        Subscription subscription = service.activate(userId, PlanType.LIFETIME, "MOCK", "MOCK-3");

        assertThat(subscription.getStartsAt()).isAfterOrEqualTo(before);
        assertThat(subscription.getExpiresAt()).isNull();
        assertThat(user.getPremiumExpiresAt()).isNull();
    }

    @Test
    void expiredPremiumRestartsFromNow() {
        givenUser(true, Instant.now().minus(Duration.ofDays(1)));
        Instant before = Instant.now();

        Subscription subscription = service.activate(userId, PlanType.MONTHLY, "MOCK", "MOCK-4");

        assertThat(subscription.getStartsAt()).isAfterOrEqualTo(before);
        assertThat(subscription.getExpiresAt()).isEqualTo(subscription.getStartsAt().plus(Duration.ofDays(30)));
    }

    @Test
    void lifetimePremiumCannotBuyAnotherPlan() {
        givenUser(true, null);

        assertApiException(() -> service.activate(userId, PlanType.YEARLY, "MOCK", "MOCK-5"),
                HttpStatus.CONFLICT, "Already have lifetime Premium");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertApiException(() -> service.activate(userId, PlanType.MONTHLY, "MOCK", "MOCK-6"),
                HttpStatus.UNAUTHORIZED, "User not found");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void expireDueSubscriptionsUsesTheSameInstantForBothUpdates() {
        when(subscriptionRepository.expireDue(any(Instant.class))).thenReturn(2);
        when(userRepository.downgradeExpiredPremium(any(Instant.class))).thenReturn(3);
        Instant before = Instant.now();

        service.expireDueSubscriptions();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> subscriptionNow = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> userNow = ArgumentCaptor.forClass(Instant.class);
        verify(subscriptionRepository).expireDue(subscriptionNow.capture());
        verify(userRepository).downgradeExpiredPremium(userNow.capture());
        assertThat(subscriptionNow.getValue()).isEqualTo(userNow.getValue());
        assertThat(subscriptionNow.getValue()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void expireDueSubscriptionsStillRunsBothUpdatesWhenNothingIsDue() {
        service.expireDueSubscriptions();

        verify(subscriptionRepository).expireDue(any(Instant.class));
        verify(userRepository).downgradeExpiredPremium(any(Instant.class));
    }

    // Tao user va cho userRepository tim thay no theo id
    private User givenUser(boolean premium, Instant premiumExpiresAt) {
        User user = new User();
        user.setId(userId);
        user.setPremium(premium);
        user.setPremiumExpiresAt(premiumExpiresAt);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }
}