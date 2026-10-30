package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.AiExamAnalysisResponse;
import com.example.lori.dto.AiExerciseResponse;
import com.example.lori.dto.AiGrammarResponse;
import com.example.lori.dto.AiVocabularyResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.dto.LearningContext.WeakItem;
import com.example.lori.dto.LearningTopicResponse;
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
import java.math.BigDecimal;
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
    void analyzeVocabularySendsSignedTextAndParsesAnalysis() throws IOException {
        startServer("/ai/vocabulary", 200, "{\"type\":\"vocabulary_analysis\",\"words\":[{\"word\":\"affect\","
                + "\"inDictionary\":true,\"ipa\":\"/efekt/\",\"definitions\":[{\"pos\":\"V\","
                + "\"meaning\":\"Lam anh huong den.\",\"example\":null}],\"explanation\":\"To change something.\","
                + "\"synonyms\":[\"influence\"],\"antonyms\":[],\"collocations\":[\"affect health\"]}],"
                + "\"difference\":null}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        AiVocabularyResponse response = client.analyzeVocabulary("affect", new LearningContext(null, null, null, null));

        assertThat(response).isEqualTo(new AiVocabularyResponse("vocabulary_analysis", List.of(
                new AiVocabularyResponse.VocabularyWord("affect", true, "/efekt/",
                        List.of(new AiVocabularyResponse.Definition("V", "Lam anh huong den.", null)),
                        "To change something.", List.of("influence"), List.of(), List.of("affect health"))), null));
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/vocabulary", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        assertThat(OBJECT_MAPPER.readTree(receivedBody.get()).get("text").asText()).isEqualTo("affect");
    }

    @Test
    void analyzeQuizSendsSignedResultAndParsesAnalysis() throws IOException {
        startServer("/ai/analyze-quiz", 200, "{\"type\":\"quiz_analysis\",\"summary\":\"You got 3 of 10.\","
                + "\"patterns\":[\"Mixes up have and has.\"],"
                + "\"recommendations\":[{\"title\":\"Review\",\"reason\":\"Weak topic.\",\"lessonId\":11}]}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        AiAnalysisResponse response = client.analyzeQuiz(new AiServiceClient.QuizPayload(
                "grammar", "Present Perfect", 3, 7, 30, new AiServiceClient.QuizHistory(12, 20, "reviewing"),
                Map.of(11, "Present Perfect"), new LearningContext(null, null, null, null)));

        assertThat(response).isEqualTo(new AiAnalysisResponse("quiz_analysis", "You got 3 of 10.",
                List.of("Mixes up have and has."),
                List.of(new AiAnalysisResponse.Recommendation("Review", "Weak topic.", 11))));
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/analyze-quiz", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("quizType").asText()).isEqualTo("grammar");
        assertThat(sent.get("accuracyPercent").asInt()).isEqualTo(30);
        assertThat(sent.get("history").get("status").asText()).isEqualTo("reviewing");
        assertThat(sent.get("lessons").get("11").asText()).isEqualTo("Present Perfect");
    }

    @Test
    void analyzeExamSendsSignedResultAndParsesAnalysis() throws IOException {
        startServer("/ai/analyze-exam", 200, "{\"type\":\"exam_analysis\",\"summary\":\"Reading is weaker.\","
                + "\"patterns\":[],\"recommendations\":[]}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        AiAnalysisResponse response = client.analyzeExam(new AiServiceClient.ExamPayload(
                "TOEIC", "Test 1", new BigDecimal("650.0"), new BigDecimal("330.0"), new BigDecimal("320.0"),
                60, 100, 70, 100, 5700,
                List.of(new AiExamAnalysisResponse.PartStat("Part 5", "READING", 18, 30, 60)),
                List.of(new AiServiceClient.TypeStat("MULTIPLE_CHOICE", 130, 200, 65)),
                List.of(new AiServiceClient.WrongQuestion("Part 5", "He ___ the report.", null, "B: wrote")),
                Map.of(11, "Present Perfect"), new LearningContext(null, null, null, null)));

        assertThat(response).isEqualTo(new AiAnalysisResponse("exam_analysis", "Reading is weaker.", List.of(), List.of()));
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/analyze-exam", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("totalScore").decimalValue()).isEqualByComparingTo("650.0");
        assertThat(sent.get("parts").get(0).get("name").asText()).isEqualTo("Part 5");
        assertThat(sent.get("wrongQuestions").get(0).get("yourAnswer").isNull()).isTrue();
        assertThat(sent.has("resultId")).isFalse();
    }

    @Test
    void generateExerciseSendsSignedTopicAndParsesBothExerciseTypes() throws IOException {
        startServer("/ai/generate-exercise", 200, "{\"type\":\"exercise_set\",\"exercises\":["
                + "{\"type\":\"multiple_choice\",\"question\":\"She ___ here.\",\"options\":[\"is\",\"are\",\"am\",\"be\"],"
                + "\"answerIndex\":0,\"explanation\":\"Use is.\"},"
                + "{\"type\":\"fill_blank\",\"question\":\"They ___ home.\",\"answers\":[\"went\"],\"explanation\":\"Past.\"}]}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        AiExerciseResponse response = client.generateExercise(new AiServiceClient.ExercisePayload(
                "grammar", "Present Perfect", "B1", List.of("Old question"), new LearningContext(null, null, null, null)));

        assertThat(response.exercises()).hasSize(2);
        assertThat(response.exercises().get(0).answerIndex()).isZero();
        assertThat(response.exercises().get(1).answers()).containsExactly("went");
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/generate-exercise", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("category").asText()).isEqualTo("grammar");
        assertThat(sent.get("topic").asText()).isEqualTo("Present Perfect");
        assertThat(sent.get("level").asText()).isEqualTo("B1");
        assertThat(sent.get("avoidQuestions").get(0).asText()).isEqualTo("Old question");
    }

    @Test
    void recommendSendsSignedWeakTopicsAndParsesAnalysis() throws IOException {
        startServer("/ai/recommend", 200, "{\"type\":\"recommendation\",\"summary\":\"Focus here.\","
                + "\"patterns\":[],\"recommendations\":[{\"title\":\"Review\",\"reason\":\"Weak\",\"lessonId\":11}]}");
        AiServiceClient client = new AiServiceClient(baseUrl(), SECRET, OBJECT_MAPPER);

        AiAnalysisResponse response = client.recommend(new AiServiceClient.RecommendPayload(
                List.of(new LearningTopicResponse("grammar", "11", "Present Perfect", 3, 7, 30)),
                Map.of(11, "Present Perfect"), new LearningContext(null, null, null, null)));

        assertThat(response.type()).isEqualTo("recommendation");
        assertThat(response.recommendations().get(0).lessonId()).isEqualTo(11);
        String timestamp = receivedHeaders.get().getFirst(AiServiceSigner.TIMESTAMP_HEADER);
        String expectedSignature = AiServiceSigner.sign(SECRET, timestamp, "POST", "/ai/recommend", receivedBody.get());
        assertThat(receivedHeaders.get().getFirst(AiServiceSigner.SIGNATURE_HEADER)).isEqualTo(expectedSignature);
        JsonNode sent = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(sent.get("weakTopics").get(0).get("topicName").asText()).isEqualTo("Present Perfect");
        assertThat(sent.get("weakTopics").get(0).get("accuracyPercent").asInt()).isEqualTo(30);
        assertThat(sent.get("lessons").get("11").asText()).isEqualTo("Present Perfect");
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