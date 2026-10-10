package com.example.lori.dto;

import java.time.Instant;

/**
 * Goi vua kich hoat. expiresAt = null nghia la Premium vinh vien.
 * App nen goi lai GET /api/users/me de cap nhat trang thai Premium.
 */
public record SubscriptionResponse(
        String planType,
        String paymentMethod,
        String status,
        Instant startsAt,
        Instant expiresAt) {
}