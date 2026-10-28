package com.example.lori.controller;

import com.example.lori.dto.ChangePasswordRequest;
import com.example.lori.dto.LearningProfileResponse;
import com.example.lori.dto.LearningTopicResponse;
import com.example.lori.dto.UpdateProfileRequest;
import com.example.lori.dto.UserResponse;
import com.example.lori.dto.UserStatsResponse;
import com.example.lori.service.LearningEngineService;
import com.example.lori.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoint /api/users/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LearningEngineService learningEngineService;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal UUID userId) {
        return userService.getMe(userId);
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal UUID userId,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(userId, request);
    }

    @GetMapping("/me/stats")
    public UserStatsResponse getStats(@AuthenticationPrincipal UUID userId) {
        return userService.getStats(userId);
    }

    @GetMapping("/me/weaknesses")
    public List<LearningTopicResponse> getWeaknesses(@AuthenticationPrincipal UUID userId) {
        return learningEngineService.getWeaknesses(userId);
    }

    @GetMapping("/me/learning-profile")
    public LearningProfileResponse getLearningProfile(@AuthenticationPrincipal UUID userId) {
        return learningEngineService.getLearningProfile(userId);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UUID userId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
    }
}