package com.example.lori.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Danh sach tien trinh chua dong bo. Toi da 500 muc/lan (so muc hoc toi da la 67 + 30 + 30 = 127,
 * nen 500 la du rong nhung van chan request qua lon).
 */
public record SyncProgressRequest(
        @NotNull @Size(max = 500) List<@NotNull @Valid ProgressItemRequest> items) {
}