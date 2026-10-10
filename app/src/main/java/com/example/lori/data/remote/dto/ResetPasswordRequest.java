package com.example.lori.data.remote.dto;

//Body của POST /api/auth/reset-password (code là mã 6 chữ số gửi qua email)
public class ResetPasswordRequest {
    public final String email;
    public final String code;
    public final String newPassword;

    public ResetPasswordRequest(String email, String code, String newPassword) {
        this.email = email;
        this.code = code;
        this.newPassword = newPassword;
    }
}