package com.example.lori.data.remote.dto;

//Body của POST /api/auth/login
public class LoginRequest {
    public final String email;
    public final String password;

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}