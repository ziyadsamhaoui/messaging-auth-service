package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.config.TokenHashEncoder;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.ResetPasswordRequest;
import com.ziyadsamhaoui.messagingauthservice.exception.InvalidTokenException;
import com.ziyadsamhaoui.messagingauthservice.model.Credential;
import com.ziyadsamhaoui.messagingauthservice.model.PasswordResetToken;
import com.ziyadsamhaoui.messagingauthservice.repository.CredentialRepository;
import com.ziyadsamhaoui.messagingauthservice.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository resetTokenRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHashEncoder tokenHashEncoder;
    private final AuthProperties properties;
    private final ResetEmailRateLimiter resetEmailRateLimiter;
    private final ResetEmailSender resetEmailSender;

    // Deliberately NOT @Transactional: the token insert must commit regardless of the
    // SMTP outcome (mail outages are transient, tokens are reusable), while the
    // EmailDeliveryException still propagates so the API surfaces a 502. markSent is
    // skipped on failure, so a failed attempt never consumes the resend cooldown.
    public void forgotPassword(String email) {
        Optional<Credential> credential = credentialRepository.findByEmailIgnoreCase(email);
        if (credential.isEmpty()) {
            return;
        }
        // Reject before consuming quota or persisting anything when delivery is unavailable.
        resetEmailSender.assertAvailable();
        resetEmailRateLimiter.consumeSlot(email);
        String rawToken = generateRawToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(credential.get().getId())
                .tokenHash(tokenHashEncoder.hash(rawToken))
                .expiryDate(Instant.now().plus(properties.passwordReset().tokenTtl()))
                .build();
        resetTokenRepository.save(token);
        resetEmailSender.sendResetEmail(email, rawToken);
        resetEmailRateLimiter.markSent(email);
        log.info("password reset email dispatched for {}", email);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        String tokenHash = tokenHashEncoder.hash(request.token());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("invalid reset token"));
        if (token.isExpiredAt(now)) {
            token.markExpired();
            throw new InvalidTokenException("reset token expired");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("reset token already used");
        }
        Credential credential = credentialRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("invalid reset token"));
        credential.changePassword(passwordEncoder.encode(request.newPassword()));
        token.markUsed();
        resetTokenRepository.invalidateAllForUser(token.getUserId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
}
