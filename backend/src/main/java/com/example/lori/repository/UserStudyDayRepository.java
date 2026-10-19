package com.example.lori.repository;

import com.example.lori.entity.UserStudyDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UserStudyDayRepository extends JpaRepository<UserStudyDay, UUID> {

    /**
     * Ghi 1 ngay hoc; da co dong cho (user, ngay) nay roi thi bo qua (khong bao loi trung khoa).
     * Tra ve 1 neu day la ngay hoc moi, 0 neu da co tu truoc.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_study_days (user_id, study_date)
            VALUES (:userId, :studyDate)
            ON CONFLICT (user_id, study_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("studyDate") LocalDate studyDate);

    /** Toan bo ngay hoc cua mot user, moi nhat truoc (moi ngay chi co 1 dong). */
    @Query("select d.studyDate from UserStudyDay d where d.userId = :userId order by d.studyDate desc")
    List<LocalDate> findStudyDates(@Param("userId") UUID userId);
}