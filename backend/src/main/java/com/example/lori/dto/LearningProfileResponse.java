package com.example.lori.dto;

import java.util.List;

/**
 * Tom tat ho so hoc tap: trinh do uoc tinh (A1/A2/B1/B2/C1), diem manh (chu de da "mastered", ty le dung cao truoc)
 * va diem yeu (chu de dang "reviewing", ty le dung thap truoc).
 */
public record LearningProfileResponse(
        String estimatedLevel,
        List<LearningTopicResponse> strengths,
        List<LearningTopicResponse> weaknesses) {
}