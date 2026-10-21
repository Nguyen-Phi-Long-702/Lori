package com.example.lori.service;

import com.example.lori.exception.ApiException;
import com.example.lori.security.AiServiceSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Goi AI Service (FastAPI) kem chu ky Server-to-Server, khong dung JWT cua user
 * Chua cau hinh secret hoac AI Service loi/khong toi duoc -> 503; Backend van khoi dong binh thuong.
 */
@Slf4j
@Service
public class AiServiceClient {

    private static final String PING_PATH = "/ai/ping";
    private static final int MIN_SECRET_LENGTH = 32;

    private final RestClient restClient;
    private final String sharedSecret;

    public AiServiceClient(
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.shared-secret}") String sharedSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
        this.sharedSecret = sharedSecret;
    }

    public PingResponse ping() {
        if (sharedSecret.length() < MIN_SECRET_LENGTH) {
            log.error("AI_SERVICE_SHARED_SECRET chua duoc cau hinh hoac ngan hon {} ky tu", MIN_SECRET_LENGTH);
            throw unavailable();
        }

        // Ky va gui CUNG mot mang byte, tranh lech giua noi dung da ky va noi dung that su gui di
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = AiServiceSigner.sign(sharedSecret, timestamp, "POST", PING_PATH, body);

        try {
            PingResponse response = restClient.post()
                    .uri(PING_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AiServiceSigner.TIMESTAMP_HEADER, timestamp)
                    .header(AiServiceSigner.SIGNATURE_HEADER, signature)
                    .body(body)
                    .retrieve()
                    .body(PingResponse.class);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (RestClientException e) {
            log.error("Goi AI Service that bai: {}", e.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable");
    }

    /** Phan hoi cua AI Service: {"status": "ok"}. */
    public record PingResponse(String status) {
    }
}