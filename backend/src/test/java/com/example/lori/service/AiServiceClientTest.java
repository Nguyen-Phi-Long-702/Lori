package com.example.lori.service;

import com.example.lori.exception.ApiException;
import com.example.lori.security.AiServiceSigner;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dung AI Service gia (HttpServer cua JDK) de kiem tra request that su Backend gui di.
 */
class AiServiceClientTest {

    private static final String SECRET = "lori-test-shared-secret-0123456789abcdef";

    private final AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pingSendsSignatureThatMatchesTheRequest() throws IOException {
        startServer(200, "{\"status\":\"ok\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET);

        AiServiceClient.PingResponse response = client.ping();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(receivedMethod.get()).isEqualTo("POST");
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/ping", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        assertThat(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp))).isLessThan(60);
    }

    @Test
    void pingFailsWithoutSendingRequestWhenSecretNotConfigured() throws IOException {
        startServer(200, "{\"status\":\"ok\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), "");

        assertThatThrownBy(client::ping).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(receivedHeaders.get()).isNull();
    }

    @Test
    void pingFailsWith503WhenAiServiceRejectsRequest() throws IOException {
        startServer(401, "{\"detail\":\"Unauthorized\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET);

        assertThatThrownBy(client::ping).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    private void startServer(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/ping", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedHeaders.set(exchange.getRequestHeaders());
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