package com.example.lori.repository;

import com.example.lori.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {
    /** Lich su thi cua mot user, moi nhat truoc. */
    List<ExamResult> findByUserIdOrderBySubmittedAtDesc(UUID userId);

    /** Ket qua theo id, chi khi thuoc ve user nay (tranh xem ket qua cua nguoi khac). */
    Optional<ExamResult> findByIdAndUserId(UUID id, UUID userId);

    /** Co lan nop nao cua user cho de nay tu moc `from` tro di khong (dung de gioi han EXP de thi moi ngay). */
    boolean existsByUserIdAndPaperIdAndSubmittedAtGreaterThanEqual(UUID userId, UUID paperId, Instant from);
}