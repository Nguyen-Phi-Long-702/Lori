package com.example.lori.service;

import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.AiGrammarResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.dto.LearningContext.WeakItem;
import com.example.lori.exception.ApiException;
import com.example.lori.security.AiServiceSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dung AI Service gia (HttpServer cua JDK) de kiem tra request that su Backend gui di.
 */
class AiServiceClientTest {

    private static final String SECRET = "lori-test-shared-secret-0123456789abcdef";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        startServer("/ai/ping", 200, "{\"status\":\"ok\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

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
        startServer("/ai/ping", 200, "{\"status\":\"ok\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), "", OBJECT_MAPPER);

        assertThatThrownBy(client::ping).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(receivedHeaders.get()).isNull();
    }

    @Test
    void pingFailsWith503WhenAiServiceRejectsRequest() throws IOException {
        startServer("/ai/ping", 401, "{\"detail\":\"Unauthorized\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        assertThatThrownBy(client::ping).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void chatSendsSignedContextAndParsesAnswer() throws IOException {
        startServer("/ai/chat", 200, "{\"type\":\"chat\",\"answer\":\"Hi there\",\"truncated\":false}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);
        LearningContext context = new LearningContext(
                List.of(new WeakItem("Present Perfect", 5, 6, 45)), null, null, null);

        AiChatResponse response = client.chat("Hello", context);

        assertThat(response).isEqualTo(new AiChatResponse("chat", "Hi there", false));
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/chat", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("message").asText()).isEqualTo("Hello");
        assertThat(sent.get("learningContext").get("weakGrammar").get(0).get("name").asText())
                .isEqualTo("Present Perfect");
        assertThat(sent.get("learningContext").has("recentExams")).isFalse();
    }

    @Test
    void analyzeGrammarSendsSignedTextAndLessonsAndParsesAnalysis() throws IOException {
        startServer("/ai/grammar", 200, "{\"type\":\"grammar_analysis\",\"correct\":false,"
                + "\"correctedSentence\":\"She doesn't like coffee.\","
                + "\"errors\":[{\"type\":\"Subject-verb agreement\",\"explanation\":\"Use doesn't.\"}],\"lessonId\":1}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        AiGrammarResponse response = client.analyzeGrammar(
                "She don't like coffee.", Map.of(1, "Present Simple"), new LearningContext(null, null, null, null));

        assertThat(response).isEqualTo(new AiGrammarResponse("grammar_analysis", false, "She doesn't like coffee.",
                List.of(new AiGrammarResponse.GrammarError("Subject-verb agreement", "Use doesn't.")), 1));
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/grammar", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("text").asText()).isEqualTo("She don't like coffee.");
        assertThat(sent.get("lessons").get("1").asText()).isEqualTo("Present Simple");
    }

    @Test
    void chatFailsWith400WhenAiServiceRejectsTheMessage() throws IOException {
        startServer("/ai/chat", 400, "{\"detail\":\"Invalid input\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        assertThatThrownBy(() -> client.chat("<b></b>", null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void chatFailsWith503WhenAiProviderFails() throws IOException {
        startServer("/ai/chat", 502, "{\"detail\":\"AI provider error\"}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        assertThatThrownBy(() -> client.chat("Hello", null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    private void startServer(String path, int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
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