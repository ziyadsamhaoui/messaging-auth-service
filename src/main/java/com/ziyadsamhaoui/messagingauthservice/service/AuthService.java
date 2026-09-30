package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.client.UserClient;
import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.config.TokenHashEncoder;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.LoginRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.LogoutRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.RefreshRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.RegisterRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.InternalRequests.CreateUserRequest;
import com.ziyadsamhaoui.messagingauthservice.exception.AccountLockedException;
import com.ziyadsamhaoui.messagingauthservice.exception.DuplicateEmailException;
import com.ziyadsamhaoui.messagingauthservice.exception.InvalidCredentialsException;
import com.ziyadsamhaoui.messagingauthservice.exception.InvalidTokenException;
import com.ziyadsamhaoui.messagingauthservice.model.Credential;
import com.ziyadsamhaoui.messagingauthservice.model.RefreshToken;
import com.ziyadsamhaoui.messagingauthservice.model.Role;
import com.ziyadsamhaoui.messagingauthservice.outbox.CredentialEvents;
import com.ziyadsamhaoui.messagingauthservice.outbox.OutboxPublisher;
import com.ziyadsamhaoui.messagingauthservice.outbox.TransactionalOutboxPublisher;
import com.ziyadsamhaoui.messagingauthservice.repository.CredentialRepository;
import com.ziyadsamhaoui.messagingauthservice.repository.RefreshTokenRepository;
import com.ziyadsamhaoui.messagingauthservice.security.JwtTokenProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenSecurityService refreshTokenSecurityService;
    private final PasswordEncoder passwordEncoder;
    private final TokenHashEncoder tokenHashEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtDenylistService jwtDenylistService;
    private final UserClient userClient;
    private final AuthProperties properties;
    private final OutboxPublisher outboxPublisher;
    private final CredentialLockService credentialLockService;

    @Transactional
    public UUID register(RegisterRequest request) {
        credentialRepository.findByEmailIgnoreCase(request.email()).ifPresent(existing -> {
            throw new DuplicateEmailException(request.email());
        });
        UUID id = UUID.randomUUID();
        Credential credential = Credential.builder()
                .id(id)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .failedLoginAttempts(0)
                .locked(false)
                .build();
        try {
            credentialRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(request.email());
        }
        try {
            userClient.createUser(new CreateUserRequest(id, request.username()));
        } catch (RuntimeException ex) {
            credentialRepository.delete(credential);
            log.warn("registration rolled back for {}: {}", request.email(), ex.getMessage());
            throw ex;
        }
        // Sprint 6 §2.1: outbox row in the SAME transaction as the insert — appended
        // after the synchronous User call succeeded so a compensated registration
        // never leaves an orphan CREDENTIAL_REGISTERED behind. Additive to the sync
        // call, not a replacement for it (dual path, §2.1).
        outboxPublisher.publish(TransactionalOutboxPublisher.AGGREGATE_TYPE, id.toString(),
                CredentialEvents.CREDENTIAL_REGISTERED, new CredentialEvents.CredentialRegistered(
                        id, request.username(), request.email(), credential.getCreatedAt()));
        return id;
    }

    // No @Transactional: the failed-attempt counter must be committed even though
    // the login itself is rejected with InvalidCredentialsException.
    public TokenPair login(LoginRequest request) {
        Instant now = Instant.now();
        Credential credential = credentialRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (credential.isLockedOut(now)) {
            throw new AccountLockedException(credential.getLockoutEnd());
        }
        if (credential.isLocked()) {
            // Lock window elapsed: auto-unlock before evaluating credentials, exactly as
            // before — but now the flip commits with a CREDENTIAL_UNLOCKED outbox row.
            credentialLockService.persistUnlock(credential);
        }
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            // Counter update + (on the threshold) CREDENTIAL_LOCKED row commit in one
            // transaction via CredentialLockService; login itself still returns 401.
            credentialLockService.persistFailedAttempt(credential, now, properties.lockout().duration());
            throw new InvalidCredentialsException();
        }
        credential.registerSuccessfulLogin();
        credentialRepository.saveAndFlush(credential);
        return issueTokenPair(credential, now);
    }

    @Transactional
    public TokenPair refresh(RefreshRequest request) {
        Instant now = Instant.now();
        String tokenHash = tokenHashEncoder.hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidCredentialsException::new);
        UUID userId = stored.getUserId();
        if (stored.isRevoked()) {
            refreshTokenSecurityService.revokeChain(userId);
            throw new InvalidTokenException("refresh token reuse detected");
        }
        if (stored.isExpiredAt(now)) {
            refreshTokenSecurityService.markExpired(stored.getId());
            throw new InvalidTokenException("refresh token expired");
        }
        stored.revoke();
        Credential credential = credentialRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        return issueTokenPair(credential, now);
    }

    @Transactional
    public void logout(LogoutRequest request, String accessToken) {
        String tokenHash = tokenHashEncoder.hash(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(RefreshToken::revoke);
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            JWTClaimsSet claims = jwtTokenProvider.parseAndVerify(accessToken);
            String jti = claims.getJWTID();
            Instant expiration = claims.getExpirationTime().toInstant();
            Duration ttl = Duration.between(Instant.now(), expiration);
            jwtDenylistService.deny(jti, ttl);
        } catch (RuntimeException ex) {
            log.warn("failed to denylist access token on logout", ex);
        }
    }

    private TokenPair issueTokenPair(Credential credential, Instant now) {
        String accessToken = jwtTokenProvider.issueAccessToken(credential.getId(), credential.getRole());
        String refreshToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .userId(credential.getId())
                .tokenHash(tokenHashEncoder.hash(refreshToken))
                .expiryDate(now.plus(properties.jwt().refreshTokenTtl()))
                .build();
        refreshTokenRepository.save(entity);
        return new TokenPair(accessToken, refreshToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
