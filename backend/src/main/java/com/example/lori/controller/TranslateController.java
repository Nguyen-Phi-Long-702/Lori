package com.example.lori.controller;

import com.example.lori.dto.TranslateRequest;
import com.example.lori.dto.TranslateResponse;
import com.example.lori.service.TranslateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint /api/translate: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()).
 * Tinh nang Free nen KHONG gan @RequirePremium.
 */
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    @PostMapping
    public TranslateResponse translate(@Valid @RequestBody TranslateRequest request) {
        return translateService.translate(request);
    }
}