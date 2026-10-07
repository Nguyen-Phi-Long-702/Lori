package com.example.lori.controller;

import com.example.lori.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test AuthController: chay Spring context that (Security + Flyway + JPA + Redis).
 * PostgreSQL va Redis la container tam do Testcontainers dung trong Docker, khong cham vao Neon/Upstash.
 * Cac gia tri bi mat o duoi chi la gia tri gia dung cho test.
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
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Test@12345";

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

    @Autowired
    private UserRepository userRepository;

    @Value("${app.security.api-key}")
    private String apiKey;

    @Value("${app.security.app-signature}")
    private String appSignature;

    // ===== Luong day du: register -> login -> protected -> refresh -> logout =====

    @Test
    void luongAuthDayDu_registerLoginMeRefreshLogout() throws Exception {
        String email = newEmail();

        // 1. register -> 201 + cap token, user duoc luu vao DB
        postJson("/api/auth/register", registerJson(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        assertThat(userRepository.existsByEmail(email)).isTrue();

        // 2. login -> 200 + cap token
        JsonNode loggedIn = readJson(postJson("/api/auth/login", loginJson(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()));
        String accessToken = loggedIn.get("accessToken").asText();
        String refreshToken = loggedIn.get("refreshToken").asText();

        // 3. protected: GET /api/users/me voi access token -> 200
        mockMvc.perform(get("/api/users/me")
                        .header("X-API-Key", apiKey)
                        .header("X-App-Signature", appSignature)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.premium").value(false));

        // 4. refresh -> 200, refresh token moi khac token cu (xoay vong)
        JsonNode refreshed = readJson(postJson("/api/auth/refresh", refreshJson(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()));
        String newRefreshToken = refreshed.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // 5. logout -> 204
        postJson("/api/auth/logout", refreshJson(newRefreshToken))
                .andExpect(status().isNoContent());

        // 6. refresh bang token da logout -> 401
        postJson("/api/auth/refresh", refreshJson(newRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    // ===== Cac truong hop loi cua tung buoc =====

    @Test
    void register_emailDaTonTai_tra409() throws Exception {
        String email = newEmail();
        postJson("/api/auth/register", registerJson(email)).andExpect(status().isCreated());

        postJson("/api/auth/register", registerJson(email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void login_saiMatKhau_tra401() throws Exception {
        String email = newEmail();
        postJson("/api/auth/register", registerJson(email)).andExpect(status().isCreated());

        postJson("/api/auth/login", loginJson(email, "WrongPass@999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protected_khongCoToken_tra401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("X-API-Key", apiKey)
                        .header("X-App-Signature", appSignature))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void refresh_dungLaiTokenCu_tra401() throws Exception {
        String oldRefreshToken = readJson(postJson("/api/auth/register", registerJson(newEmail()))
                .andExpect(status().isCreated()))
                .get("refreshToken").asText();

        postJson("/api/auth/refresh", refreshJson(oldRefreshToken)).andExpect(status().isOk());

        postJson("/api/auth/refresh", refreshJson(oldRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    // ===== Ham ho tro =====

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .header("X-API-Key", apiKey)
                .header("X-App-Signature", appSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String newEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String email) {
        return """
                {"displayName": "Test User", "email": "%s", "password": "%s"}
                """.formatted(email, PASSWORD);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private static String refreshJson(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }
}