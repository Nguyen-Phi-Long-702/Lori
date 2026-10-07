package com.example.lori.dto;

import java.time.Instant;

/** Mot muc tien trinh tren server, tra ve cho app khi pull. */
public record ProgressItemResponse(
        String itemType,
        int itemId,
        String status,
        int correctCount,
        int incorrectCount,
        Instant lastStudiedAt) {
}