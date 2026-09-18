package com.ziyadsamhaoui.messagingauthservice.repository;

import com.ziyadsamhaoui.messagingauthservice.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalseAndExpiredFalseAndExpiryDateAfter(
            UUID userId, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("update RefreshToken t set t.expired = true where t.id = :id and t.expired = false")
    int markExpiredById(@Param("id") Long id);

    @Modifying
    @Query("update RefreshToken t set t.expired = true where t.userId = :userId and t.expiryDate < :now and t.expired = false")
    int markExpiredTokens(@Param("userId") UUID userId, @Param("now") Instant now);
}
