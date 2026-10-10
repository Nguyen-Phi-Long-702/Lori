package com.example.lori.data.remote.dto;

//Body của PUT /api/users/me
public class UpdateProfileRequest {
    public final String displayName;

    public UpdateProfileRequest(String displayName) {
        this.displayName = displayName;
    }
}