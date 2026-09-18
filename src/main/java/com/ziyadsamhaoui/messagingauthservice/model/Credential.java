package com.ziyadsamhaoui.messagingauthservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Credential {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "lockout_end")
    private Instant lockoutEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isLockedOut(Instant now) {
        return locked && lockoutEnd != null && lockoutEnd.isAfter(now);
    }

    public void registerFailedAttempt(Instant now, Instant lockoutEnd) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5) {
            locked = true;
            this.lockoutEnd = lockoutEnd;
        }
    }

    public void unlock() {
        locked = false;
        lockoutEnd = null;
        failedLoginAttempts = 0;
    }

    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        locked = false;
        lockoutEnd = null;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }
}
