package com.example.lori.service;

import com.example.lori.dto.MockPurchaseRequest;
import com.example.lori.dto.SubscriptionResponse;
import com.example.lori.entity.Subscription;
import com.example.lori.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thanh toan. Hien chi co Mock (ke hoach muc 13.3): khong goi ben ngoai, kich hoat goi ngay.
 * Google Play / VNPay / Momo (optional) se them sau va cung goi SubscriptionService.activate.
 */
@Service
public class PaymentService {

    private static final String PAYMENT_METHOD_MOCK = "MOCK";

    private final SubscriptionService subscriptionService;
    private final boolean mockEnabled;

    public PaymentService(SubscriptionService subscriptionService,
                          @Value("${app.payment.mock-enabled}") boolean mockEnabled) {
        this.subscriptionService = subscriptionService;
        this.mockEnabled = mockEnabled;
    }

    public SubscriptionResponse mockPurchase(UUID userId, MockPurchaseRequest request) {
        if (!mockEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Mock purchase is disabled");
        }
        // transaction_id la UNIQUE nen moi lan mua phai khac nhau
        String transactionId = PAYMENT_METHOD_MOCK + "-" + UUID.randomUUID();
        Subscription subscription = subscriptionService.activate(
                userId, request.planType(), PAYMENT_METHOD_MOCK, transactionId);
        return new SubscriptionResponse(
                subscription.getPlanType(),
                subscription.getPaymentMethod(),
                subscription.getStatus(),
                subscription.getStartsAt(),
                subscription.getExpiresAt());
    }
}