package com.example.lori.controller;

import com.example.lori.dto.ContentUpdatesResponse;
import com.example.lori.dto.ContentVersionResponse;
import com.example.lori.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint /api/content/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/version")
    public ContentVersionResponse getVersion() {
        return contentService.getVersion();
    }

    @GetMapping("/updates")
    public ContentUpdatesResponse getUpdates(@RequestParam(defaultValue = "0") int sinceVersion) {
        return contentService.getUpdates(sinceVersion);
    }
}