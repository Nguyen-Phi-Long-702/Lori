package com.example.lori.data.remote.dto;

//Body của POST /api/auth/refresh và POST /api/auth/logout
public class RefreshTokenRequest {
    public final String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}