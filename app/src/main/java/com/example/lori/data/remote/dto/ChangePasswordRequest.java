package com.example.lori.data.remote.dto;

//Body của PUT /api/users/me/password
public class ChangePasswordRequest {
    public final String currentPassword;
    public final String newPassword;

    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }
}