package com.example.lori.controller;

import com.example.lori.dto.UpdateProfileRequest;
import com.example.lori.dto.UserResponse;
import com.example.lori.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoint /api/users/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal UUID userId) {
        return userService.getMe(userId);
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal UUID userId,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(userId, request);
    }
}