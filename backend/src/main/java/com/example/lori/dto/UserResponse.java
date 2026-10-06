package com.example.lori.dto;

import java.time.Instant;
import java.util.UUID;

/** Thong tin ho so tra ve cho app; khong chua passwordHash/googleId. */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        boolean premium,
        Instant premiumExpiresAt,
        Instant createdAt) {
}