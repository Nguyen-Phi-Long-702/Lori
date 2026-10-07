package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Mot muc tien trinh tu user_progress_local cua Android (item_type + item_id xac dinh muc hoc). */
public record ProgressItemRequest(
        @NotBlank @Size(max = 50) String itemType,
        @NotNull @Positive Integer itemId,
        @NotBlank @Size(max = 30) String status,
        @PositiveOrZero int correctCount,
        @PositiveOrZero int incorrectCount,
        @NotNull Instant lastStudiedAt) {
}