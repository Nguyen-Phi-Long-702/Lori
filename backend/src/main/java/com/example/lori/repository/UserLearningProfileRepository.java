package com.example.lori.repository;

import com.example.lori.entity.UserLearningProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserLearningProfileRepository extends JpaRepository<UserLearningProfile, UUID> {

    Optional<UserLearningProfile> findByUserId(UUID userId);
}