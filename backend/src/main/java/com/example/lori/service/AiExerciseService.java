package com.example.lori.service;

import com.example.lori.dto.AiExerciseResponse;
import com.example.lori.dto.AiGenerateExerciseRequest;
import com.example.lori.entity.AiGeneratedExercise;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.AiGeneratedExerciseRepository;
import com.example.lori.service.AiServiceClient.ExercisePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Module 5.7 AI Generated Exercises: sinh bai tap cho 1 bai ngu phap / chu de tu vung. AI Service kiem tra dinh dang
 * tung bai truoc khi tra ve; moi bai duoc luu vao ai_generated_exercise voi is_validated = false (cho nguoi kiem tra).
 * Do kho theo trinh do uoc tinh cua Learning Engine; gui kem cac cau da sinh truoc do de AI khong lap lai.
 * Khong gan @Transactional o day de khong giu ket noi DB trong luc cho LLM tra loi. Khong gui userId sang AI Service.
 */
@Service
@RequiredArgsConstructor
public class AiExerciseService {

    private static final String CATEGORY_GRAMMAR = "grammar";

    private final AiRateLimiter aiRateLimiter;
    private final LearningContextService learningContextService;
    private final LearningEngineService learningEngineService;
    private final AiGeneratedExerciseRepository exerciseRepository;
    private final AiServiceClient aiServiceClient;

    public AiExerciseResponse generate(UUID userId, AiGenerateExerciseRequest request) {
        aiRateLimiter.checkGenerateExercise(userId);
        boolean grammar = CATEGORY_GRAMMAR.equals(request.category());
        String topic = (grammar ? learningContextService.grammarLessonNames() : learningContextService.vocabTopicNames())
                .get(request.topicId());
        if (topic == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown topic");
        }

        String sourceWeakness = request.category() + ":" + request.topicId();
        List<String> earlierQuestions = exerciseRepository
                .findTop10ByUserIdAndSourceWeaknessOrderByCreatedAtDesc(userId, sourceWeakness).stream()
                .map(saved -> saved.getContent().question())
                .toList();
        AiExerciseResponse generated = aiServiceClient.generateExercise(new ExercisePayload(
                request.category(),
                topic,
                learningEngineService.getLearningProfile(userId).estimatedLevel(),
                earlierQuestions,
                learningContextService.build(userId, AiTaskType.GENERATE_EXERCISE)));

        exerciseRepository.saveAll(generated.exercises().stream()
                .map(exercise -> toEntity(userId, sourceWeakness, exercise))
                .toList());
        return generated;
    }

    private static AiGeneratedExercise toEntity(UUID userId, String sourceWeakness, AiExerciseResponse.Exercise exercise) {
        AiGeneratedExercise saved = new AiGeneratedExercise();
        saved.setUserId(userId);
        saved.setExerciseType(exercise.type());
        saved.setContent(exercise);
        saved.setSourceWeakness(sourceWeakness);
        return saved;
    }
}