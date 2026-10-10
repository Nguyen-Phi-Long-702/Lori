package com.example.lori.controller;

import com.example.lori.dto.MockPurchaseRequest;
import com.example.lori.dto.SubscriptionResponse;
import com.example.lori.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoint /api/payment/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/mock-purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse mockPurchase(@AuthenticationPrincipal UUID userId,
                                             @Valid @RequestBody MockPurchaseRequest request) {
        return paymentService.mockPurchase(userId, request);
    }
}