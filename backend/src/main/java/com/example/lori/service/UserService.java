package com.example.lori.service;

import com.example.lori.dto.UpdateProfileRequest;
import com.example.lori.dto.UserResponse;
import com.example.lori.entity.User;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.UserRepository;
import com.example.lori.util.InputSanitizerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Nghiep vu ho so nguoi dung hien tai (/api/users/me). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        String displayName = InputSanitizerUtil.sanitize(request.displayName());
        if (displayName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Display name is invalid");
        }
        user.setDisplayName(displayName);
        return toResponse(user);
    }

    // Token con han nhung user da bi xoa -> 401 de app di vao luong dang nhap lai
    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.isPremium(),
                user.getPremiumExpiresAt(),
                user.getCreatedAt());
    }
}