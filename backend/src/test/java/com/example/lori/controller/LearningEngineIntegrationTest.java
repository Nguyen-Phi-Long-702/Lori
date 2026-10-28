package com.example.lori.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test Learning Engine: sync tien trinh quiz -> GET /api/users/me/weaknesses va /learning-profile.
 * PostgreSQL va Redis la container tam do Testcontainers dung trong Docker, khong cham vao Neon/Upstash.
 * Ngay hoc dat o qua khu xa (hon 7 ngay) nen ExpService khong cong EXP/streak, chi con Learning Engine duoc kiem tra.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.jwt.secret=test-jwt-secret-for-integration-test-0123456789",
        "app.security.api-key=test-api-key",
        "app.security.app-signature=test-app-signature",
        "app.google.client-id=test-google-client-id",
        "app.mail.brevo-api-key=test-brevo-api-key",
        "app.mail.sender-email=test@example.com"
})
@AutoConfigureMockMvc
class LearningEngineIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.security.api-key}")
    private String apiKey;

    @Value("${app.security.app-signature}")
    private String appSignature;

    @Test
    void quizSync_weakTopicsAppearAndLevelIsEstimated() throws Exception {
        String token = registerAndGetAccessToken();

        sync(token,
                item("grammar_quiz_lesson", 26, 2, 8, "2026-01-01T00:00:00Z"),
                item("vocab_quiz_topic", 13, 3, 7, "2026-01-01T00:00:00Z"),
                item("grammar_quiz_lesson", 11, 10, 0, "2026-01-01T00:00:00Z"),
                item("grammar_lesson", 27, 0, 0, "2026-01-01T00:00:00Z"));

        getWithToken("/api/users/me/weaknesses", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("grammar"))
                .andExpect(jsonPath("$[0].topicKey").value("26"))
                .andExpect(jsonPath("$[0].topicName").value("Subjunctive Mood"))
                .andExpect(jsonPath("$[0].accuracyPercent").value(20))
                .andExpect(jsonPath("$[1].category").value("vocabulary"))
                .andExpect(jsonPath("$[1].topicKey").value("13"))
                .andExpect(jsonPath("$[1].accuracyPercent").value(30));

        getWithToken("/api/users/me/learning-profile", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedLevel").value("B2"))
                .andExpect(jsonPath("$.strengths.length()").value(1))
                .andExpect(jsonPath("$.strengths[0].topicName").value("Present Perfect"))
                .andExpect(jsonPath("$.strengths[0].accuracyPercent").value(100))
                .andExpect(jsonPath("$.weaknesses.length()").value(2));
    }

    @Test
    void quizSync_addsUpOnlyNewerAttemptsAndWeaknessCanRecover() throws Exception {
        String token = registerAndGetAccessToken();
        String firstAttempt = item("grammar_quiz_lesson", 26, 2, 8, "2026-01-01T00:00:00Z");

        sync(token, firstAttempt);
        // Gui lai dung ban cu (cung lastStudiedAt) thi khong duoc cong don lan nua
        sync(token, firstAttempt);
        getWithToken("/api/users/me/weaknesses", token)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].correctCount").value(2))
                .andExpect(jsonPath("$[0].incorrectCount").value(8));

        // Lan lam moi (lastStudiedAt moi hon) dung het: cong don 12/20 = 60% nen khong con la diem yeu
        sync(token, item("grammar_quiz_lesson", 26, 10, 0, "2026-01-02T00:00:00Z"));
        getWithToken("/api/users/me/weaknesses", token)
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void sync(String token, String... items) throws Exception {
        mockMvc.perform(post("/api/progress/sync")
                        .header("X-API-Key", apiKey)
                        .header("X-App-Signature", appSignature)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [" + String.join(",", items) + "]}"))
                .andExpect(status().isOk());
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path)
                .header("X-API-Key", apiKey)
                .header("X-App-Signature", appSignature)
                .header("Authorization", "Bearer " + token));
    }

    private String registerAndGetAccessToken() throws Exception {
        String json = """
                {"displayName": "Test User", "email": "%s", "password": "Test@12345"}
                """.formatted("it-" + UUID.randomUUID() + "@example.com");
        String response = mockMvc.perform(post("/api/auth/register")
                        .header("X-API-Key", apiKey)
                        .header("X-App-Signature", appSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private static String item(String itemType, int itemId, int correct, int incorrect, String studiedAt) {
        return """
                {"itemType": "%s", "itemId": %d, "status": "completed",
                 "correctCount": %d, "incorrectCount": %d, "lastStudiedAt": "%s"}
                """.formatted(itemType, itemId, correct, incorrect, studiedAt);
    }
}