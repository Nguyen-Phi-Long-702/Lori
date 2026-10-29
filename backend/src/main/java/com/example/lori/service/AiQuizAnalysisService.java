package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiQuizAnalysisRequest;
import com.example.lori.dto.AiQuizAnalysisResponse;
import com.example.lori.entity.UserProgress;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.UserProgressRepository;
import com.example.lori.repository.UserWeaknessRecordRepository;
import com.example.lori.service.AiServiceClient.QuizHistory;
import com.example.lori.service.AiServiceClient.QuizPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Module 5.6 AI Quiz Analysis: chi DOC ket qua quiz da cham (user_progress, user_weakness_record), khong cham lai.
 * Quiz do Android cham roi dong bo len server (ProgressService.sync), nen quiz chua dong bo thi tra 404.
 * Khong gan @Transactional o day de khong giu ket noi DB trong luc cho LLM tra loi. Khong gui userId sang AI Service.
 * shortcut: server chi giu so cau dung/sai cua quiz, khong giu tung cau sai; nang cap khi app dong bo them chi tiet tung cau.
 */
@Service
@RequiredArgsConstructor
public class AiQuizAnalysisService {

    // Gia tri item_type va category khop UserService, LearningEngineService
    private static final String TYPE_VOCAB_QUIZ = "vocab_quiz_topic";
    private static final String TYPE_GRAMMAR_QUIZ = "grammar_quiz_lesson";
    private static final String CATEGORY_VOCABULARY = "vocabulary";
    private static final String CATEGORY_GRAMMAR = "grammar";

    private final AiRateLimiter aiRateLimiter;
    private final UserProgressRepository userProgressRepository;
    private final UserWeaknessRecordRepository weaknessRepository;
    private final LearningContextService learningContextService;
    private final AiServiceClient aiServiceClient;

    public AiQuizAnalysisResponse analyze(UUID userId, AiQuizAnalysisRequest request) {
        aiRateLimiter.checkAnalyzeQuiz(userId);
        boolean vocabulary = "vocabulary".equals(request.quizType());
        int itemId = request.itemId();

        UserProgress progress = userProgressRepository
                .findByUserIdAndItemTypeAndItemId(userId, vocabulary ? TYPE_VOCAB_QUIZ : TYPE_GRAMMAR_QUIZ, itemId)
                .filter(p -> p.getCorrectCount() + p.getIncorrectCount() > 0)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz result not found"));
        int correct = progress.getCorrectCount();
        int incorrect = progress.getIncorrectCount();
        int accuracy = (int) Math.round(100.0 * correct / (correct + incorrect));

        // Quiz lam truoc khi co Learning Engine chua co ket qua cong don, khi do history = null
        QuizHistory history = weaknessRepository
                .findByUserIdAndCategoryAndTopicKey(
                        userId, vocabulary ? CATEGORY_VOCABULARY : CATEGORY_GRAMMAR, String.valueOf(itemId))
                .map(weakness -> new QuizHistory(
                        weakness.getCorrectCount(), weakness.getIncorrectCount(), weakness.getStatus()))
                .orElse(null);
        Map<Integer, String> names = vocabulary
                ? learningContextService.vocabTopicNames()
                : learningContextService.grammarLessonNames();
        String title = names.getOrDefault(itemId, (vocabulary ? "Vocabulary topic " : "Grammar lesson ") + itemId);

        AiAnalysisResponse analysis = aiServiceClient.analyzeQuiz(new QuizPayload(
                request.quizType(),
                title,
                correct,
                incorrect,
                accuracy,
                history,
                learningContextService.grammarLessonNames(),
                learningContextService.build(userId, AiTaskType.ANALYZE_QUIZ)));
        return new AiQuizAnalysisResponse(
                analysis.type(), title, correct, incorrect, accuracy,
                analysis.summary(), analysis.patterns(), analysis.recommendations());
    }
}