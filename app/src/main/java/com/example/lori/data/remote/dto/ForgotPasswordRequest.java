package com.example.lori.data.remote.dto;

//Body của POST /api/auth/forgot-password
public class ForgotPasswordRequest {
    public final String email;

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }
}