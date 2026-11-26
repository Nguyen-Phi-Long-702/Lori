package com.example.lori.dto;

import com.example.lori.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @MaxUtf8Bytes(72) String currentPassword,
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String newPassword) {
}
