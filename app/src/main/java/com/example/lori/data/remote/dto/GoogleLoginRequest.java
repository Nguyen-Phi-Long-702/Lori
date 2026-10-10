package com.example.lori.data.remote.dto;

//Body của POST /api/auth/google (ID Token lấy từ Google Sign-In ở Ngày 3-4)
public class GoogleLoginRequest {
    public final String idToken;

    public GoogleLoginRequest(String idToken) {
        this.idToken = idToken;
    }
}