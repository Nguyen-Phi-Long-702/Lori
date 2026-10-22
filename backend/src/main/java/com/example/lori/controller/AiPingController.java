package com.example.lori.controller;

import com.example.lori.service.AiServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint TAM de kiem chung Backend (Render) goi duoc AI Service (Vercel) kem chu ky Server-to-Server.
 * Yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()). Se go sau khi kiem chung xong.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPingController {

    private final AiServiceClient aiServiceClient;

    @GetMapping("/ping")
    public AiServiceClient.PingResponse ping() {
        return aiServiceClient.ping();
    }
}