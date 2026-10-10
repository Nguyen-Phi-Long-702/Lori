package com.example.lori.entity;

/**
 * Goi Premium. days = so ngay hieu luc; null = vinh vien (subscriptions.expires_at va
 * users.premium_expires_at de NULL). Gia tien khong luu o Backend (xem UI Premium).
 * Gia tri ten enum duoc luu vao subscriptions.plan_type.
 */
public enum PlanType {
    MONTHLY(30),
    YEARLY(365),
    LIFETIME(null);

    private final Integer days;

    PlanType(Integer days) {
        this.days = days;
    }

    public Integer getDays() {
        return days;
    }

    public boolean isLifetime() {
        return days == null;
    }
}