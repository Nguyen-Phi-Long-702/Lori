package com.example.lori.controller;

import com.example.lori.dto.ProgressItemResponse;
import com.example.lori.dto.SyncProgressRequest;
import com.example.lori.dto.SyncProgressResponse;
import com.example.lori.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoint /api/progress/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @PostMapping("/sync")
    public SyncProgressResponse sync(@AuthenticationPrincipal UUID userId,
                                     @Valid @RequestBody SyncProgressRequest request) {
        return progressService.sync(userId, request);
    }

    @GetMapping("/pull")
    public List<ProgressItemResponse> pull(@AuthenticationPrincipal UUID userId) {
        return progressService.pull(userId);
    }
}