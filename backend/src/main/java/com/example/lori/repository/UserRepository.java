package com.example.lori.repository;

import com.example.lori.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    /**
     * Ha quyen Premium cua user da het han (is_premium = true, co han va han <= now).
     * Premium vinh vien (premium_expires_at null) khong bi anh huong. Tra ve so user da ha quyen.
     */
    @Modifying
    @Query("update User u set u.premium = false "
            + "where u.premium = true and u.premiumExpiresAt is not null and u.premiumExpiresAt <= :now")
    int downgradeExpiredPremium(@Param("now") Instant now);
}