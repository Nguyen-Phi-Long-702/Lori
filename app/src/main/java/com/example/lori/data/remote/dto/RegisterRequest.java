package com.example.lori.data.remote.dto;

//Body của POST /api/auth/register (khớp RegisterRequest của Backend)
public class RegisterRequest {
    public final String displayName;
    public final String email;
    public final String password;

    public RegisterRequest(String displayName, String email, String password) {
        this.displayName = displayName;
        this.email = email;
        this.password = password;
    }
}