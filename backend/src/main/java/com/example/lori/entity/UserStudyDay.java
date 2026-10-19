package com.example.lori.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Anh xa bang user_study_days (V4__exp_streak.sql): moi dong la 1 ngay user da hoc (ngay theo gio Viet Nam).
 * Dong duoc ghi bang cau lenh native o UserStudyDayRepository.insertIfAbsent; entity nay chu yeu dung de doc.
 */
@Entity
@Table(name = "user_study_days")
@Getter
@Setter
@NoArgsConstructor
public class UserStudyDay {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;
}