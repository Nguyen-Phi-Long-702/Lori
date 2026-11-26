package com.example.lori.dto;

import com.example.lori.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @MaxUtf8Bytes(72) String password) {
}
