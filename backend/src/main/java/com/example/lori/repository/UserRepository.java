package com.example.lori.repository;

import com.example.lori.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /**
     * Cong them EXP bang 1 cau lenh nguyen tu tren DB (khong doc roi ghi lai), nen hai request cung luc
     * (vd dong bo tien trinh va nop bai thi) khong ghi de len nhau. Tra ve so dong da cap nhat.
     */
    @Modifying
    @Query(value = "UPDATE users SET total_exp = total_exp + :amount WHERE id = :userId", nativeQuery = true)
    int addExp(@Param("userId") UUID userId, @Param("amount") int amount);

    /** Tong EXP hien tai cua user; null neu khong co user nay. */
    @Query(value = "SELECT total_exp FROM users WHERE id = :userId", nativeQuery = true)
    Integer findTotalExp(@Param("userId") UUID userId);

    /** Moi user co EXP > 0 duoi dang [id, total_exp]; dung de nap lai bang xep hang toan thoi gian vao Redis. */
    @Query(value = "SELECT id, total_exp FROM users WHERE total_exp > 0", nativeQuery = true)
    List<Object[]> findAllWithExp();
}