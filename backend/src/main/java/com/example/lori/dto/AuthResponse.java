package com.example.lori.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken) {
}