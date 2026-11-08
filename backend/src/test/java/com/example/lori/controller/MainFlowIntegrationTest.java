package com.example.lori.controller;

import com.example.lori.entity.ExamPaper;
import com.example.lori.entity.ExamQuestion;
import com.example.lori.entity.ExamSection;
import com.example.lori.repository.AiGeneratedExerciseRepository;
import com.example.lori.repository.ExamPaperRepository;
import com.example.lori.repository.ExamQuestionRepository;
import com.example.lori.repository.ExamSectionRepository;
import com.example.lori.security.AiServiceSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test luong chinh: dang ky -> dang nhap -> mua Premium -> lam de -> nop bai -> bang xep hang -> goi AI.
 * PostgreSQL va Redis la container tam do Testcontainers dung trong Docker, khong cham vao Neon/Upstash.
 * AI Service la HttpServer gia ngay trong JVM cua test (giong AiServiceClientTest) nen khong goi Vercel/Cloudflare;
 * moi request toi AI Service van duoc kiem tra chu ky HMAC that.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.jwt.secret=test-jwt-secret-for-integration-test-0123456789",
        "app.security.api-key=test-api-key",
        "app.security.app-signature=test-app-signature",
        "app.google.client-id=test-google-client-id",
        "app.mail.brevo-api-key=test-brevo-api-key",
        "app.mail.sender-email=test@example.com",
        "app.ai-service.shared-secret=" + MainFlowIntegrationTest.AI_SECRET
})
@AutoConfigureMockMvc
class MainFlowIntegrationTest {

    static final String AI_SECRET = "lori-it-ai-shared-secret-0123456789abcdef";

    private static final String PASSWORD = "Test@12345";

    private static final Map<String, String> AI_RESPONSES = Map.of(
            "/ai/chat", "{\"type\":\"chat\",\"answer\":\"Hello from fake AI\",\"truncated\":false}",
            "/ai/grammar", "{\"type\":\"grammar_analysis\",\"correct\":false,"
                    + "\"correctedSentence\":\"She doesn't like coffee.\","
                    + "\"errors\":[{\"type\":\"Subject-verb agreement\",\"explanation\":\"Use doesn't.\"}],\"lessonId\":1}",
            "/ai/vocabulary", "{\"type\":\"vocabulary_analysis\",\"words\":[{\"word\":\"affect\","
                    + "\"inDictionary\":true,\"ipa\":\"/efekt/\",\"definitions\":[{\"pos\":\"V\","
                    + "\"meaning\":\"Lam anh huong den.\",\"example\":null}],\"explanation\":\"To change something.\","
                    + "\"synonyms\":[\"influence\"],\"antonyms\":[],\"collocations\":[\"affect health\"]}],"
                    + "\"difference\":null}",
            "/ai/analyze-quiz", "{\"type\":\"quiz_analysis\",\"summary\":\"You got 2 of 10.\","
                    + "\"patterns\":[\"Mixes up moods.\"],"
                    + "\"recommendations\":[{\"title\":\"Review\",\"reason\":\"Weak topic.\",\"lessonId\":26}]}",
            "/ai/analyze-exam", "{\"type\":\"exam_analysis\",\"summary\":\"Reading is weaker.\","
                    + "\"patterns\":[],\"recommendations\":[]}",
            "/ai/generate-exercise", "{\"type\":\"exercise_set\",\"exercises\":["
                    + "{\"type\":\"multiple_choice\",\"question\":\"She ___ here.\","
                    + "\"options\":[\"is\",\"are\",\"am\",\"be\"],\"answerIndex\":0,\"explanation\":\"Use is.\"},"
                    + "{\"type\":\"fill_blank\",\"question\":\"They ___ home.\",\"answers\":[\"went\"],"
                    + "\"explanation\":\"Past.\"}]}",
            "/ai/recommend", "{\"type\":\"recommendation\",\"summary\":\"Focus here.\",\"patterns\":[],"
                    + "\"recommendations\":[{\"title\":\"Review\",\"reason\":\"Weak\",\"lessonId\":26}]}",
            "/ai/conversation", "{\"type\":\"conversation_feedback\",\"summary\":\"Tot.\","
                    + "\"grammar\":[],\"vocabulary\":[],\"naturalness\":[],\"usefulPhrases\":[]}",
            "/ai/identify-image", "{\"type\":\"image_identification\",\"objects\":[{\"word\":\"cat\","
                    + "\"ipa\":\"/kaet/\",\"meaning\":\"Con meo.\",\"confidence\":0.95,"
                    + "\"box\":{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.4},"
                    + "\"example\":\"The cat sleeps.\",\"exampleVi\":\"Con meo ngu.\"}]}");

    private static final FakeAiService AI = new FakeAiService(AI_RESPONSES);

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
        registry.add("app.ai-service.base-url", AI::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamPaperRepository examPaperRepository;

    @Autowired
    private ExamSectionRepository examSectionRepository;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private AiGeneratedExerciseRepository aiGeneratedExerciseRepository;

    @Value("${app.security.api-key}")
    private String apiKey;

    @Value("${app.security.app-signature}")
    private String appSignature;

    @BeforeEach
    void clearReceivedAiRequests() {
        AI.clear();
    }

    @AfterAll
    static void stopAiService() {
        AI.stop();
    }

    // ===== Luong chinh: register -> login -> Free bi chan -> mua Premium -> thi -> nop bai -> bang xep hang -> AI =====

    @Test
    void luongChinh_dangKyMuaPremiumThiNopBaiBangXepHangVaGoiAi() throws Exception {
        String email = newEmail();

        // 1. dang ky + dang nhap, user moi la Free
        postJson("/api/auth/register", null, registerJson(email)).andExpect(status().isCreated());
        String token = readJson(postJson("/api/auth/login", null, loginJson(email))
                .andExpect(status().isOk()))
                .get("accessToken").asText();
        getWithToken("/api/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false));

        // 2. user Free bi chan o de thi va AI; cong Premium chan truoc khi goi AI Service
        getWithToken("/api/exams", token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("PREMIUM_REQUIRED"));
        postJson("/api/ai/chat", token, "{\"message\": \"Hello\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("PREMIUM_REQUIRED"));
        assertThat(AI.received("/ai/chat")).isNull();

        // 3. mua Premium (mock) -> user thanh Premium
        postJson("/api/payment/mock-purchase", token, "{\"planType\": \"MONTHLY\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planType").value("MONTHLY"))
                .andExpect(jsonPath("$.paymentMethod").value("MOCK"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        getWithToken("/api/users/me", token)
                .andExpect(jsonPath("$.premium").value(true));

        // 4. de thi: danh sach, chi tiet (khong lo dap an), bat dau
        SeededExam exam = seedExam();
        getWithToken("/api/exams", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].totalQuestions".formatted(exam.paperId())).value(4));
        getWithToken("/api/exams/" + exam.paperId(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].questions.length()").value(2))
                .andExpect(jsonPath("$.sections[0].questions[0].correctAnswer").doesNotExist());
        String sessionToken = readJson(postJson("/api/exams/" + exam.paperId() + "/start", token, "{}")
                .andExpect(status().isOk()))
                .get("sessionToken").asText();

        // 5. nop bai: dung 3/4 cau, Backend tu cham diem (TOEIC: Listening 2/2 = 495, Reading 1/2 = 250)
        String submitJson = submitJson(sessionToken, exam);
        String resultId = readJson(postJson("/api/exams/" + exam.paperId() + "/submit", token, submitJson)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctCount").value(3))
                .andExpect(jsonPath("$.totalQuestions").value(4))
                .andExpect(jsonPath("$.listeningScore").value(495.0))
                .andExpect(jsonPath("$.readingScore").value(250.0))
                .andExpect(jsonPath("$.totalScore").value(745.0)))
                .get("id").asText();

        // 6. nop lai bang token cu bi tu choi (Redis GETDEL that), lich su co dung 1 ket qua
        postJson("/api/exams/" + exam.paperId() + "/submit", token, submitJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired exam session"));
        getWithToken("/api/exams/results", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(resultId));

        // 7. bang xep hang: 20 EXP ngay hoc dau tien + 163 EXP bai thi (50 + 150 * 3/4) = 183, o ca 3 bang
        getWithToken("/api/leaderboard/global/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.score").value(183));
        getWithToken("/api/leaderboard/weekly/me", token)
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.score").value(183));
        getWithToken("/api/leaderboard/monthly/me", token)
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.score").value(183));
        getWithToken("/api/leaderboard/global?page=0&size=5", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Test User"))
                .andExpect(jsonPath("$[0].score").value(183));

        // 8. AI chat: Premium duoc goi AI Service; request duoc ky, khong co userId, context theo AiContextPolicy (CHAT khong co recentExams)
        postJson("/api/ai/chat", token, "{\"message\": \"What is the present perfect?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("chat"))
                .andExpect(jsonPath("$.answer").value("Hello from fake AI"));
        JsonNode chatSent = sentJson("/ai/chat");
        assertThat(chatSent.get("message").asText()).isEqualTo("What is the present perfect?");
        assertThat(chatSent.has("userId")).isFalse();
        assertThat(chatSent.get("learningContext").has("recentActivity")).isTrue();
        assertThat(chatSent.get("learningContext").has("recentExams")).isFalse();

        // 9. AI phan tich bai thi vua nop: Backend gui diem that, cau sai, thong ke tung phan; khong gui resultId
        postJson("/api/ai/analyze-exam", token, "{\"resultId\": \"%s\"}".formatted(resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("exam_analysis"))
                .andExpect(jsonPath("$.resultId").value(resultId))
                .andExpect(jsonPath("$.parts[0].name").value("Part 5 - Incomplete Sentences"))
                .andExpect(jsonPath("$.parts[0].accuracyPercent").value(50))
                .andExpect(jsonPath("$.summary").value("Reading is weaker."));
        JsonNode examSent = sentJson("/ai/analyze-exam");
        assertThat(examSent.get("totalScore").decimalValue()).isEqualByComparingTo("745.0");
        assertThat(examSent.get("wrongQuestions").size()).isEqualTo(1);
        assertThat(examSent.get("wrongQuestions").get(0).get("yourAnswer").asText()).isEqualTo("A: write");
        assertThat(examSent.get("wrongQuestions").get(0).get("correctAnswer").asText()).isEqualTo("B: wrote");
        assertThat(examSent.get("learningContext").get("recentExams").get(0).get("examType").asText())
                .isEqualTo("TOEIC");
        assertThat(examSent.has("resultId")).isFalse();
    }

    // ===== Cac endpoint AI con lai: user Premium co diem yeu tu quiz da dong bo =====

    @Test
    void moiEndpointAi_premiumCoDiemYeu_dungDuLieuVaChuKy() throws Exception {
        String token = registerPremiumUser();
        sync(token, item("grammar_quiz_lesson", 26, 2, 8, "2026-01-01T00:00:00Z"));
        UUID userId = UUID.fromString(readJson(getWithToken("/api/users/me", token)
                .andExpect(status().isOk()))
                .get("id").asText());

        // analyze-quiz: ket qua quiz do Backend doc tu user_progress, AI chi viet nhan xet
        postJson("/api/ai/analyze-quiz", token, "{\"quizType\": \"grammar\", \"itemId\": 26}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("quiz_analysis"))
                .andExpect(jsonPath("$.title").value("Subjunctive Mood"))
                .andExpect(jsonPath("$.accuracyPercent").value(20))
                .andExpect(jsonPath("$.summary").value("You got 2 of 10."));
        JsonNode quizSent = sentJson("/ai/analyze-quiz");
        assertThat(quizSent.get("accuracyPercent").asInt()).isEqualTo(20);
        assertThat(quizSent.get("learningContext").get("weakGrammar").get(0).get("name").asText())
                .isEqualTo("Subjunctive Mood");

        // recommend: Learning Engine chon chu de yeu, AI chi viet nhan xet va de xuat
        postJson("/api/ai/recommend", token, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("recommendation"))
                .andExpect(jsonPath("$.topics.length()").value(1))
                .andExpect(jsonPath("$.topics[0].topicName").value("Subjunctive Mood"))
                .andExpect(jsonPath("$.summary").value("Focus here."));
        assertThat(sentJson("/ai/recommend").get("weakTopics").get(0).get("topicKey").asText()).isEqualTo("26");

        // analyze-text nhanh grammar: Learning Context chi co diem yeu ngu phap (AiContextPolicy)
        postJson("/api/ai/analyze-text", token, "{\"type\": \"grammar\", \"text\": \"She don't like coffee.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("grammar_analysis"))
                .andExpect(jsonPath("$.correctedSentence").value("She doesn't like coffee."));
        JsonNode grammarSent = sentJson("/ai/grammar");
        assertThat(grammarSent.get("text").asText()).isEqualTo("She don't like coffee.");
        assertThat(grammarSent.get("learningContext").has("weakGrammar")).isTrue();
        assertThat(grammarSent.get("learningContext").has("weakVocabulary")).isFalse();

        // analyze-text nhanh vocabulary
        postJson("/api/ai/analyze-text", token, "{\"type\": \"vocabulary\", \"text\": \"affect\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("vocabulary_analysis"))
                .andExpect(jsonPath("$.words[0].word").value("affect"));
        assertThat(sentJson("/ai/vocabulary").get("text").asText()).isEqualTo("affect");

        // generate-exercise: luu 2 bai vao ai_generated_exercise (JSONB that); lan sau gui cac cau cu de AI khong lap lai
        String generateJson = "{\"category\": \"grammar\", \"topicId\": 26}";
        postJson("/api/ai/generate-exercise", token, generateJson)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("exercise_set"))
                .andExpect(jsonPath("$.exercises.length()").value(2));
        JsonNode firstGenerateSent = sentJson("/ai/generate-exercise");
        assertThat(firstGenerateSent.get("topic").asText()).isEqualTo("Subjunctive Mood");
        assertThat(firstGenerateSent.get("avoidQuestions").size()).isZero();
        assertThat(aiGeneratedExerciseRepository
                .findTop10ByUserIdAndSourceWeaknessOrderByCreatedAtDesc(userId, "grammar:26")).hasSize(2);
        postJson("/api/ai/generate-exercise", token, generateJson).andExpect(status().isOk());
        assertThat(sentJson("/ai/generate-exercise").get("avoidQuestions").size()).isEqualTo(2);

        // conversation: ket thuc buoi thi moi co Learning Context
        postJson("/api/ai/conversation", token, """
                {"mode": "restaurant", "messages": [{"role": "user", "content": "I am agree."}], "finish": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("conversation_feedback"))
                .andExpect(jsonPath("$.summary").value("Tot."));
        JsonNode conversationSent = sentJson("/ai/conversation");
        assertThat(conversationSent.get("mode").asText()).isEqualTo("restaurant");
        assertThat(conversationSent.get("finish").asBoolean()).isTrue();
        assertThat(conversationSent.get("learningContext").has("weakGrammar")).isTrue();

        // identify-image: Backend chuyen nguyen byte anh sang AI Service va ky tren dung cac byte do
        byte[] image = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        mockMvc.perform(multipart("/api/ai/identify-image")
                        .file(new MockMultipartFile("image", "cat.jpg", MediaType.IMAGE_JPEG_VALUE, image))
                        .header("X-API-Key", apiKey)
                        .header("X-App-Signature", appSignature)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("image_identification"))
                .andExpect(jsonPath("$.objects[0].word").value("cat"));
        assertThat(signedRequest("/ai/identify-image").body()).isEqualTo(image);
    }

    // ===== Ham ho tro =====

    private String registerPremiumUser() throws Exception {
        String token = readJson(postJson("/api/auth/register", null, registerJson(newEmail()))
                .andExpect(status().isCreated()))
                .get("accessToken").asText();
        postJson("/api/payment/mock-purchase", token, "{\"planType\": \"MONTHLY\"}")
                .andExpect(status().isCreated());
        return token;
    }

    private void sync(String token, String... items) throws Exception {
        postJson("/api/progress/sync", token, "{\"items\": [" + String.join(",", items) + "]}")
                .andExpect(status().isOk());
    }

    // Ngay hoc dat o qua khu xa (hon 7 ngay) de ExpService khong cong EXP, khong lam doi bang xep hang
    private static String item(String itemType, int itemId, int correct, int incorrect, String studiedAt) {
        return """
                {"itemType": "%s", "itemId": %d, "status": "completed",
                 "correctCount": %d, "incorrectCount": %d, "lastStudiedAt": "%s"}
                """.formatted(itemType, itemId, correct, incorrect, studiedAt);
    }

    private ResultActions postJson(String path, String token, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
                .header("X-API-Key", apiKey)
                .header("X-App-Signature", appSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path)
                .header("X-API-Key", apiKey)
                .header("X-App-Signature", appSignature)
                .header("Authorization", "Bearer " + token));
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // Request ma AI Service gia nhan duoc o path nay; chu ky phai khop voi cach Backend ky that
    private static AiRequest signedRequest(String path) {
        AiRequest request = AI.received(path);
        assertThat(request).as("AI Service nhan request " + path).isNotNull();
        assertThat(request.signature()).isEqualTo(
                AiServiceSigner.sign(AI_SECRET, request.timestamp(), "POST", path, request.body()));
        return request;
    }

    private JsonNode sentJson(String path) throws IOException {
        return objectMapper.readTree(signedRequest(path).body());
    }

    private static String newEmail() {
        return "flow-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String email) {
        return """
                {"displayName": "Test User", "email": "%s", "password": "%s"}
                """.formatted(email, PASSWORD);
    }

    private static String loginJson(String email) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, PASSWORD);
    }

    // Dap an dung: Listening A, B; Reading C, B. Bai lam gui len dung 3/4: sai cau Reading thu hai (chon A thay vi B)
    private static String submitJson(String sessionToken, SeededExam exam) {
        return """
                {"sessionToken": "%s", "answers": [
                  {"questionId": "%s", "answer": "A"},
                  {"questionId": "%s", "answer": "B"},
                  {"questionId": "%s", "answer": "C"},
                  {"questionId": "%s", "answer": "A"}
                ]}
                """.formatted(sessionToken, exam.listening1(), exam.listening2(), exam.reading1(), exam.reading2());
    }

    // De TOEIC nho (2 cau Listening + 2 cau Reading); tieu de section theo dang that de AiExamAnalysisService gom ve Part
    private SeededExam seedExam() {
        ExamPaper paper = new ExamPaper();
        paper.setExamType("TOEIC");
        paper.setTitle("IT TOEIC " + UUID.randomUUID());
        paper.setDifficulty("EASY");
        paper.setDurationMinutes(120);
        paper = examPaperRepository.save(paper);

        ExamSection listening = section(paper.getId(), "LISTENING", "Part 2 - Question-Response (Q1-Q2)", 1);
        ExamSection reading = section(paper.getId(), "READING", "Part 5 - Incomplete Sentences (Q3-Q4)", 2);
        List<String> audioOptions = List.of("Yes, at nine.", "No, thanks.", "On the table.");
        return new SeededExam(
                paper.getId(),
                question(listening, 1, null, audioOptions, "A"),
                question(listening, 2, null, audioOptions, "B"),
                question(reading, 1, "The report ___ by the manager yesterday.",
                        List.of("review", "reviews", "was reviewed"), "C"),
                question(reading, 2, "He ___ the report yesterday.",
                        List.of("write", "wrote", "written"), "B"));
    }

    private ExamSection section(UUID paperId, String skill, String title, int orderIndex) {
        ExamSection section = new ExamSection();
        section.setPaperId(paperId);
        section.setSkill(skill);
        section.setTitle(title);
        section.setOrderIndex(orderIndex);
        return examSectionRepository.save(section);
    }

    private UUID question(ExamSection section, int orderIndex, String text, List<String> options, String correct) {
        ExamQuestion question = new ExamQuestion();
        question.setSectionId(section.getId());
        question.setOrderIndex(orderIndex);
        question.setQuestionType("MULTIPLE_CHOICE");
        question.setQuestionText(text);
        question.setOptions(options);
        question.setCorrectAnswer(correct);
        return examQuestionRepository.save(question).getId();
    }

    private record SeededExam(UUID paperId, UUID listening1, UUID listening2, UUID reading1, UUID reading2) {
    }

    private record AiRequest(byte[] body, String timestamp, String signature) {
    }

    /** AI Service gia: tra JSON dung san theo path, va nho request cuoi cung nhan duoc o moi path. */
    private static final class FakeAiService {

        private final HttpServer server;
        private final Map<String, AiRequest> received = new ConcurrentHashMap<>();

        FakeAiService(Map<String, String> responses) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/ai/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                received.put(path, new AiRequest(
                        exchange.getRequestBody().readAllBytes(),
                        exchange.getRequestHeaders().getFirst(AiServiceSigner.TIMESTAMP_HEADER),
                        exchange.getRequestHeaders().getFirst(AiServiceSigner.SIGNATURE_HEADER)));
                String json = responses.get(path);
                if (json == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                byte[] out = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        AiRequest received(String path) {
            return received.get(path);
        }

        void clear() {
            received.clear();
        }

        void stop() {
            server.stop(0);
        }
    }
}