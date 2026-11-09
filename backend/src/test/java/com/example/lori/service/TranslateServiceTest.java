package com.example.lori.service;

import com.example.lori.dto.TranslateRequest;
import com.example.lori.dto.TranslateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;

/** Dung LibreTranslate gia (HttpServer cua JDK) de kiem tra request that su Backend gui di. */
class TranslateServiceTest {

    private static final String UNAVAILABLE = "Translation service unavailable";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void translatesAndSendsTheTrimmedTextWithBothLanguages() throws IOException {
        startServer(200, "{\"translatedText\":\"Xin chao\"}");

        TranslateResponse response = new TranslateService(baseUrl())
                .translate(new TranslateRequest("  Hello  ", "en", "vi"));

        assertThat(response.translatedText()).isEqualTo("Xin chao");
        assertThat(receivedMethod.get()).isEqualTo("POST");
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("q").asText()).isEqualTo("Hello");
        assertThat(sent.get("source").asText()).isEqualTo("en");
        assertThat(sent.get("target").asText()).isEqualTo("vi");
        assertThat(sent.get("format").asText()).isEqualTo("text");
    }

    @Test
    void sameSourceAndTargetLanguageIs400() {
        TranslateService service = new TranslateService("http://127.0.0.1:1");

        assertApiException(() -> service.translate(new TranslateRequest("Hello", "en", "en")),
                HttpStatus.BAD_REQUEST, "Source and target language must be different");
    }

    @Test
    void translatorErrorIs503() throws IOException {
        startServer(500, "{\"error\":\"boom\"}");
        TranslateService service = new TranslateService(baseUrl());

        assertApiException(() -> service.translate(new TranslateRequest("Hello", "en", "vi")),
                HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    @Test
    void responseWithoutTranslatedTextIs503() throws IOException {
        startServer(200, "{}");
        TranslateService service = new TranslateService(baseUrl());

        assertApiException(() -> service.translate(new TranslateRequest("Hello", "en", "vi")),
                HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    @Test
    void unreachableTranslatorIs503() throws IOException {
        startServer(200, "{}");
        String closedUrl = baseUrl();
        server.stop(0);
        server = null;
        TranslateService service = new TranslateService(closedUrl);

        assertApiException(() -> service.translate(new TranslateRequest("Hello", "en", "vi")),
                HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    private void startServer(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}