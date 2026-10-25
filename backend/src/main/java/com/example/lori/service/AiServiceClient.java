package com.example.lori.service;

import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.exception.ApiException;
import com.example.lori.security.AiServiceSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Goi AI Service (FastAPI) kem chu ky Server-to-Server, khong dung JWT cua user
 * Chua cau hinh secret hoac AI Service loi/khong toi duoc -> 503; Backend van khoi dong binh thuong.
 * Rieng AI Service tra 400 (cau hoi rong sau khi lam sach) -> 400.
 */
@Slf4j
@Service
public class AiServiceClient {

    private static final String PING_PATH = "/ai/ping";
    private static final String CHAT_PATH = "/ai/chat";
    private static final int MIN_SECRET_LENGTH = 32;

    private final RestClient restClient;
    private final String sharedSecret;
    private final ObjectMapper objectMapper;

    public AiServiceClient(
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.shared-secret}") String sharedSecret,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
        this.sharedSecret = sharedSecret;
        this.objectMapper = objectMapper;
    }

    public PingResponse ping() {
        return post(PING_PATH, "{}".getBytes(StandardCharsets.UTF_8), PingResponse.class);
    }

    public AiChatResponse chat(String message, LearningContext learningContext) {
        return post(CHAT_PATH, toJson(new ChatPayload(message, learningContext)), AiChatResponse.class);
    }

    private <T> T post(String path, byte[] body, Class<T> responseType) {
        if (sharedSecret.length() < MIN_SECRET_LENGTH) {
            log.error("AI_SERVICE_SHARED_SECRET chua duoc cau hinh hoac ngan hon {} ky tu", MIN_SECRET_LENGTH);
            throw unavailable();
        }

        // Ky va gui CUNG mot mang byte, tranh lech giua noi dung da ky va noi dung that su gui di
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = AiServiceSigner.sign(sharedSecret, timestamp, "POST", path, body);

        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AiServiceSigner.TIMESTAMP_HEADER, timestamp)
                    .header(AiServiceSigner.SIGNATURE_HEADER, signature)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (HttpClientErrorException.BadRequest e) {
            // AI Service chi tra 400 khi cau hoi rong sau khi lam sach (InvalidInputError)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message is empty after cleaning");
        } catch (RestClientException e) {
            log.error("Goi AI Service that bai: {}", e.getMessage());
            throw unavailable();
        }
    }

    private byte[] toJson(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Khong tao duoc JSON gui AI Service", e);
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable");
    }

    /** Body gui /ai/chat: ten truong khop ChatRequest ben AI Service (learningContext viet camelCase). */
    public record ChatPayload(String message, LearningContext learningContext) {
    }

    /** Phan hoi cua AI Service: {"status": "ok"}. */
    public record PingResponse(String status) {
    }
}