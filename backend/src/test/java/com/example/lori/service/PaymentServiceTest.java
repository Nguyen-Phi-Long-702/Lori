package com.example.lori.service;

import com.example.lori.dto.MockPurchaseRequest;
import com.example.lori.dto.SubscriptionResponse;
import com.example.lori.entity.PlanType;
import com.example.lori.entity.Subscription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final UUID userId = UUID.randomUUID();

    @Test
    void disabledMockPurchaseIsForbidden() {
        PaymentService service = new PaymentService(subscriptionService, false);

        assertApiException(() -> service.mockPurchase(userId, new MockPurchaseRequest(PlanType.MONTHLY)),
                HttpStatus.FORBIDDEN, "Mock purchase is disabled");

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void enabledMockPurchaseActivatesThePlanAndReturnsTheSubscription() {
        Instant startsAt = Instant.now();
        Instant expiresAt = startsAt.plus(Duration.ofDays(365));
        Subscription subscription = new Subscription();
        subscription.setPlanType("YEARLY");
        subscription.setPaymentMethod("MOCK");
        subscription.setStatus("ACTIVE");
        subscription.setStartsAt(startsAt);
        subscription.setExpiresAt(expiresAt);
        when(subscriptionService.activate(eq(userId), eq(PlanType.YEARLY), eq("MOCK"), anyString()))
                .thenReturn(subscription);
        PaymentService service = new PaymentService(subscriptionService, true);

        SubscriptionResponse response = service.mockPurchase(userId, new MockPurchaseRequest(PlanType.YEARLY));

        assertThat(response).isEqualTo(new SubscriptionResponse("YEARLY", "MOCK", "ACTIVE", startsAt, expiresAt));
    }

    @Test
    void everyMockPurchaseGetsItsOwnTransactionId() {
        when(subscriptionService.activate(any(UUID.class), any(PlanType.class), anyString(), anyString()))
                .thenReturn(new Subscription());
        PaymentService service = new PaymentService(subscriptionService, true);

        service.mockPurchase(userId, new MockPurchaseRequest(PlanType.MONTHLY));
        service.mockPurchase(userId, new MockPurchaseRequest(PlanType.MONTHLY));

        ArgumentCaptor<String> transactionIds = ArgumentCaptor.forClass(String.class);
        verify(subscriptionService, times(2))
                .activate(eq(userId), eq(PlanType.MONTHLY), eq("MOCK"), transactionIds.capture());
        assertThat(transactionIds.getAllValues())
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allSatisfy(transactionId -> assertThat(transactionId).startsWith("MOCK-"));
    }
}