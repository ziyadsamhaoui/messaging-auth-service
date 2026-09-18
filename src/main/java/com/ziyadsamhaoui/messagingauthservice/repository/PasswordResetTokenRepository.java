package com.ziyadsamhaoui.messagingauthservice.repository;

import com.ziyadsamhaoui.messagingauthservice.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PasswordResetToken t set t.used = true where t.userId = :userId and t.used = false")
    void invalidateAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("update PasswordResetToken t set t.expired = true where t.userId = :userId and t.expiryDate < :now and t.expired = false")
    void markExpiredTokens(@Param("userId") UUID userId, @Param("now") Instant now);
}
