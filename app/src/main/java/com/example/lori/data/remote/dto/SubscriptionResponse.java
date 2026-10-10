package com.example.lori.data.remote.dto;

//Gói vừa kích hoạt. expiresAt = null nghĩa là Premium vĩnh viễn (LIFETIME)
public class SubscriptionResponse {
    public String planType;
    public String paymentMethod;
    public String status;
    public String startsAt;
    public String expiresAt;
}