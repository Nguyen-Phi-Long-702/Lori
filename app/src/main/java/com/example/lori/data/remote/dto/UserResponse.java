package com.example.lori.data.remote.dto;

//Hồ sơ user từ GET/PUT /api/users/me. Thời gian là chuỗi ISO-8601 (vd 2026-10-10T04:59:40Z), có thể null
public class UserResponse {
    public String id;
    public String email;
    public String displayName;
    public String avatarUrl;
    public boolean premium;
    public String premiumExpiresAt;
    public String createdAt;
}