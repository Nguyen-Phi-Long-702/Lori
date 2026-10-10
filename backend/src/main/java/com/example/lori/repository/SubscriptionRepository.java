package com.example.lori.repository;

import com.example.lori.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Danh dau EXPIRED cac goi ACTIVE da qua han (expires_at null = vinh vien thi bo qua).
     * Chuoi 'ACTIVE' / 'EXPIRED' phai khop voi hang so trong SubscriptionService.
     * Tra ve so dong da cap nhat.
     */
    @Modifying
    @Query("update Subscription s set s.status = 'EXPIRED' "
            + "where s.status = 'ACTIVE' and s.expiresAt is not null and s.expiresAt <= :now")
    int expireDue(@Param("now") Instant now);
}