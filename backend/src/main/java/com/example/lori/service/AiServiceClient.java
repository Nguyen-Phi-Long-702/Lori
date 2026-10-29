package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.AiExamAnalysisResponse;
import com.example.lori.dto.AiGrammarResponse;
import com.example.lori.dto.AiVocabularyResponse;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Goi AI Service (FastAPI) kem chu ky Server-to-Server, khong dung JWT cua user
 * Chua cau hinh secret hoac AI Service loi/khong toi duoc -> 503; Backend van khoi dong binh thuong.
 * Rieng AI Service tra 400 (cau hoi/cau/tu can phan tich khong hop le sau khi lam sach) -> 400.
 */
@Slf4j
@Service
public class AiServiceClient {

    private static final String PING_PATH = "/ai/ping";
    private static final String CHAT_PATH = "/ai/chat";
    private static final String GRAMMAR_PATH = "/ai/grammar";
    private static final String VOCABULARY_PATH = "/ai/vocabulary";
    private static final String ANALYZE_QUIZ_PATH = "/ai/analyze-quiz";
    private static final String ANALYZE_EXAM_PATH = "/ai/analyze-exam";
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

    public AiGrammarResponse analyzeGrammar(String text, Map<Integer, String> lessons, LearningContext learningContext) {
        return post(GRAMMAR_PATH, toJson(new GrammarPayload(text, lessons, learningContext)), AiGrammarResponse.class);
    }

    public AiVocabularyResponse analyzeVocabulary(String text, LearningContext learningContext) {
        return post(VOCABULARY_PATH, toJson(new VocabularyPayload(text, learningContext)), AiVocabularyResponse.class);
    }

    public AiAnalysisResponse analyzeQuiz(QuizPayload payload) {
        return post(ANALYZE_QUIZ_PATH, toJson(payload), AiAnalysisResponse.class);
    }

    public AiAnalysisResponse analyzeExam(ExamPayload payload) {
        return post(ANALYZE_EXAM_PATH, toJson(payload), AiAnalysisResponse.class);
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
            // AI Service chi tra 400 khi input khong hop le (InvalidInputError)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid input");
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

    /** Body gui /ai/grammar: lessons la {id: ten} cac bai ngu phap cua Lori, AI chi duoc lien ket toi id trong do. */
    public record GrammarPayload(String text, Map<Integer, String> lessons, LearningContext learningContext) {
    }

    /** Body gui /ai/vocabulary: text la 1 hoac 2 tu/cum tu user nhap. */
    public record VocabularyPayload(String text, LearningContext learningContext) {
    }

    /**
     * Body gui /ai/analyze-quiz: ket qua quiz do Backend cham, ten truong khop QuizAnalysisRequest ben AI Service.
     * history = null neu Backend chua co ket qua cong don cua chu de nay; lessons nhu GrammarPayload.
     */
    public record QuizPayload(
            String quizType, String title, int correctCount, int incorrectCount, int accuracyPercent,
            QuizHistory history, Map<Integer, String> lessons, LearningContext learningContext) {
    }

    /** Ket qua cong don cua chu de (user_weakness_record); status do Learning Engine quyet dinh. */
    public record QuizHistory(int correctCount, int incorrectCount, String status) {
    }

    /**
     * Body gui /ai/analyze-exam: diem chinh thuc va thong ke do Backend tinh, ten truong khop ExamAnalysisRequest
     * ben AI Service. Khong co id cua user, ket qua hay de thi.
     */
    public record ExamPayload(
            String examType, String title, BigDecimal totalScore, BigDecimal listeningScore, BigDecimal readingScore,
            int listeningCorrect, int listeningTotal, int readingCorrect, int readingTotal, int timeSpentSeconds,
            List<AiExamAnalysisResponse.PartStat> parts, List<TypeStat> questionTypes,
            List<WrongQuestion> wrongQuestions, Map<Integer, String> lessons, LearningContext learningContext) {
    }

    /** Thong ke theo loai cau hoi (MULTIPLE_CHOICE, MATCHING...). */
    public record TypeStat(String name, int correctCount, int totalCount, int accuracyPercent) {
    }

    /** Mot cau sai: yourAnswer = null neu user bo trong; dap an dang chu cai kem noi dung lua chon, vd "B: wrote". */
    public record WrongQuestion(String part, String question, String yourAnswer, String correctAnswer) {
    }

    /** Phan hoi cua AI Service: {"status": "ok"}. */
    public record PingResponse(String status) {
    }
}