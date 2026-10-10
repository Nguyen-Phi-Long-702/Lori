package com.example.lori.service;

import com.example.lori.dto.TranslateRequest;
import com.example.lori.dto.TranslateResponse;
import com.example.lori.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Dich cau/doan EN <-> VI bang LibreTranslate (POST /translate). Khong log noi dung nguoi dung nhap.
 * LibreTranslate loi/khong toi duoc -> 503 de app bao "thu lai sau".
 */
@Slf4j
@Service
public class TranslateService {

    private final RestClient restClient;

    public TranslateService(@Value("${app.translate.base-url}") String baseUrl) {
        // Dat timeout de request khong treo mai; lan dich dau sau khi khoi dong co the cham hon binh thuong
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    public TranslateResponse translate(TranslateRequest request) {
        if (request.sourceLang().equals(request.targetLang())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source and target language must be different");
        }

        Map<String, Object> body = Map.of(
                "q", request.text().trim(),
                "source", request.sourceLang(),
                "target", request.targetLang(),
                "format", "text");

        try {
            LibreTranslateResponse response = restClient.post()
                    .uri("/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(LibreTranslateResponse.class);
            if (response == null || response.translatedText() == null) {
                throw unavailable();
            }
            return new TranslateResponse(response.translatedText());
        } catch (RestClientException e) {
            // Chi log thong bao loi, khong log noi dung nguoi dung
            log.error("Goi LibreTranslate that bai: {}", e.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Translation service unavailable");
    }

    /** Phan hoi cua LibreTranslate: {"translatedText": "..."}. */
    public record LibreTranslateResponse(String translatedText) {
    }
}