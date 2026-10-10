package com.example.lori.dto;

import com.example.lori.entity.PlanType;
import jakarta.validation.constraints.NotNull;

/** Yeu cau mua Premium mock: planType = MONTHLY / YEARLY / LIFETIME. */
public record MockPurchaseRequest(@NotNull PlanType planType) {
}