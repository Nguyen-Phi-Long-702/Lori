package com.example.lori.data.remote.dto;

//Body của POST /api/payment/mock-purchase. planType: MONTHLY, YEARLY hoặc LIFETIME
public class MockPurchaseRequest {
    public final String planType;

    public MockPurchaseRequest(String planType) {
        this.planType = planType;
    }
}