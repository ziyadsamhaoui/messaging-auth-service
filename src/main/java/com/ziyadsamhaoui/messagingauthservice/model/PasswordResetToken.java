package com.ziyadsamhaoui.messagingauthservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "is_used", nullable = false)
    private boolean used;

    @Column(name = "is_expired", nullable = false)
    private boolean expired;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    public boolean isConsumable(Instant now) {
        return !used && !expired && expiryDate.isAfter(now);
    }

    public boolean isExpiredAt(Instant now) {
        return !expiryDate.isAfter(now);
    }

    public void markUsed() {
        this.used = true;
    }

    public void markExpired() {
        this.expired = true;
    }
}
