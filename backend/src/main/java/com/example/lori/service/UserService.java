package com.example.lori.service;

import com.example.lori.dto.UpdateProfileRequest;
import com.example.lori.dto.UserResponse;
import com.example.lori.dto.UserStatsResponse;
import com.example.lori.entity.User;
import com.example.lori.entity.UserProgress;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.UserProgressRepository;
import com.example.lori.repository.UserRepository;
import com.example.lori.util.InputSanitizerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Nghiep vu ho so nguoi dung hien tai (/api/users/me). */
@Service
@RequiredArgsConstructor
public class UserService {

    // Gia tri item_type / status do app Android ghi vao user_progress_local (UserProgressLocal)
    private static final String STATUS_COMPLETED = "completed";
    private static final String TYPE_GRAMMAR_LESSON = "grammar_lesson";
    private static final String TYPE_VOCAB_QUIZ = "vocab_quiz_topic";
    private static final String TYPE_GRAMMAR_QUIZ = "grammar_quiz_lesson";

    private final UserRepository userRepository;
    private final UserProgressRepository userProgressRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        String displayName = InputSanitizerUtil.sanitize(request.displayName());
        if (displayName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Display name is invalid");
        }
        user.setDisplayName(displayName);
        return toResponse(user);
    }

    /** Thong ke tu user_progress: so muc "completed" theo loai + tong dung/sai cua moi dong (moi trang thai). */
    @Transactional(readOnly = true)
    public UserStatsResponse getStats(UUID userId) {
        long grammarLessons = 0;
        long vocabQuizTopics = 0;
        long grammarQuizLessons = 0;
        long totalCorrect = 0;
        long totalIncorrect = 0;

        for (UserProgress progress : userProgressRepository.findByUserId(userId)) {
            totalCorrect += progress.getCorrectCount();
            totalIncorrect += progress.getIncorrectCount();
            if (!STATUS_COMPLETED.equals(progress.getStatus())) {
                continue;
            }
            switch (progress.getItemType()) {
                case TYPE_GRAMMAR_LESSON -> grammarLessons++;
                case TYPE_VOCAB_QUIZ -> vocabQuizTopics++;
                case TYPE_GRAMMAR_QUIZ -> grammarQuizLessons++;
                default -> {
                }
            }
        }
        return new UserStatsResponse(grammarLessons, vocabQuizTopics, grammarQuizLessons, totalCorrect, totalIncorrect);
    }

    // Token con han nhung user da bi xoa -> 401 de app di vao luong dang nhap lai
    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.isPremium(),
                user.getPremiumExpiresAt(),
                user.getCreatedAt());
    }
}