package com.ziyadsamhaoui.messagingauthservice.outbox;

import com.ziyadsamhaoui.messagingauthservice.client.UserClient;
import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.config.TokenHashEncoder;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.LoginRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.RegisterRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.ResetPasswordRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.InternalRequests.CreateUserRequest;
import com.ziyadsamhaoui.messagingauthservice.model.Credential;
import com.ziyadsamhaoui.messagingauthservice.model.PasswordResetToken;
import com.ziyadsamhaoui.messagingauthservice.repository.CredentialRepository;
import com.ziyadsamhaoui.messagingauthservice.repository.PasswordResetTokenRepository;
import com.ziyadsamhaoui.messagingauthservice.repository.RefreshTokenRepository;
import com.ziyadsamhaoui.messagingauthservice.security.JwtTokenProvider;
import com.ziyadsamhaoui.messagingauthservice.service.AuthService;
import com.ziyadsamhaoui.messagingauthservice.service.CredentialLockService;
import com.ziyadsamhaoui.messagingauthservice.service.PasswordResetService;
import com.ziyadsamhaoui.messagingauthservice.service.ResetEmailRateLimiter;
import com.ziyadsamhaoui.messagingauthservice.service.ResetEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 6 §3.5 (pure unit variant, no Docker): proves the same-transaction rule
 * is wired correctly — every domain write that should produce an event appends an
 * outbox row via the MANDATORY publisher inside the service transaction, and none
 * is appended where no domain write happens.
 */
@ExtendWith(MockitoExtension.class)
class OutboxFlowTest {

    @Mock
    private CredentialRepository credentialRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private UserClient userClient;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private ResetEmailRateLimiter resetEmailRateLimiter;
    @Mock
    private ResetEmailSender resetEmailSender;

    private AuthService authService;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt("issuer", "messaging-api", null, Duration.ofMinutes(15), Duration.ofDays(7)),
                new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthProperties.PasswordReset(Duration.ofHours(1)),
                new AuthProperties.Email(null, Duration.ofMinutes(2), 20, 3, Duration.ofMinutes(5)));
        CredentialLockService lockService = new CredentialLockService(credentialRepository, outboxPublisher);
        authService = new AuthService(credentialRepository, refreshTokenRepository,
                mock(com.ziyadsamhaoui.messagingauthservice.service.RefreshTokenSecurityService.class),
                passwordEncoder, new TokenHashEncoder(), jwtTokenProvider,
                mock(com.ziyadsamhaoui.messagingauthservice.service.JwtDenylistService.class),
                userClient, properties, outboxPublisher, lockService);
        passwordResetService = new PasswordResetService(resetTokenRepository, credentialRepository, passwordEncoder,
                new TokenHashEncoder(), properties, resetEmailRateLimiter, resetEmailSender, outboxPublisher);
    }

    @Test
    void registrationAppendsCredRegisteredOutboxRow() {
        when(credentialRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(credentialRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Credential saved = inv.getArgument(0);
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(Instant.now());
            }
            return saved;
        });

        authService.register(new RegisterRequest("new@example.com", "newuser", "SecurePassword123!"));

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("Credential"), id.capture(), type.capture(), payload.capture());
        assertThat(type.getValue()).isEqualTo(CredentialEvents.CREDENTIAL_REGISTERED);
        assertThat(payload.getValue()).isInstanceOf(CredentialEvents.CredentialRegistered.class);
        CredentialEvents.CredentialRegistered registered = (CredentialEvents.CredentialRegistered) payload.getValue();
        assertThat(registered.credentialId().toString()).isEqualTo(id.getValue());
        assertThat(registered.username()).isEqualTo("newuser");
        assertThat(registered.email()).isEqualTo("new@example.com");
        assertThat(registered.createdAt()).isNotNull();
        // Sync call still happens (dual path, additive — not replaced by the event).
        verify(userClient).createUser(any(CreateUserRequest.class));
    }

    @Test
    void compensatedRegistrationAppendsNoOutboxRow() {
        when(credentialRepository.findByEmailIgnoreCase("fail@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(credentialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("user service down")).when(userClient).createUser(any(CreateUserRequest.class));

        try {
            authService.register(new RegisterRequest("fail@example.com", "failuser", "SecurePassword123!"));
        } catch (RuntimeException expected) {
            // propagating failure
        }

        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
        verify(credentialRepository).delete(any(Credential.class));
    }

    @Test
    void fifthFailedAttemptEmitsCredLocked() {
        Credential credential = Credential.builder()
                .id(UUID.randomUUID())
                .email("lock@example.com")
                .passwordHash("hashed")
                .failedLoginAttempts(4)
                .build();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(credentialRepository.findByEmailIgnoreCase("lock@example.com")).thenReturn(Optional.of(credential));
        when(credentialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        try {
            authService.login(new LoginRequest("lock@example.com", "WrongPassword1!"));
        } catch (RuntimeException expected) {
            // 401 path
        }

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("Credential"), eq(credential.getId().toString()), type.capture(),
                payload.capture());
        assertThat(type.getValue()).isEqualTo(CredentialEvents.CREDENTIAL_LOCKED);
        assertThat(((CredentialEvents.CredentialLocked) payload.getValue()).lockoutEnd()).isNotNull();
    }

    @Test
    void fourthFailedAttemptEmitsNoEvent() {
        Credential credential = Credential.builder()
                .id(UUID.randomUUID())
                .email("under@example.com")
                .passwordHash("hashed")
                .failedLoginAttempts(3)
                .build();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(credentialRepository.findByEmailIgnoreCase("under@example.com")).thenReturn(Optional.of(credential));
        when(credentialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        try {
            authService.login(new LoginRequest("under@example.com", "WrongPassword1!"));
        } catch (RuntimeException expected) {
            // 401 path
        }

        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void autoUnlockAfterWindowEmitsCredUnlocked() {
        Credential credential = Credential.builder()
                .id(UUID.randomUUID())
                .email("unlock@example.com")
                .passwordHash("hashed")
                .locked(true)
                .lockoutEnd(Instant.now().minusSeconds(60))
                .build();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(credentialRepository.findByEmailIgnoreCase("unlock@example.com")).thenReturn(Optional.of(credential));
        when(credentialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.issueAccessToken(any(), any())).thenReturn("access-token");

        authService.login(new LoginRequest("unlock@example.com", "SecurePassword123!"));

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(outboxPublisher).publish(eq("Credential"), eq(credential.getId().toString()), type.capture(),
                any(Object.class));
        assertThat(type.getValue()).isEqualTo(CredentialEvents.CREDENTIAL_UNLOCKED);
    }

    @Test
    void passwordResetEmitsCredPasswordChangedWithoutSecretMaterial() {
        UUID userId = UUID.randomUUID();
        Credential credential = Credential.builder().id(userId).email("reset@example.com").passwordHash("hashed")
                .build();
        String expectedHash = new TokenHashEncoder().hash("raw-token");
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(expectedHash)
                .expiryDate(Instant.now().plusSeconds(600))
                .build();
        when(resetTokenRepository.findByTokenHash(expectedHash)).thenReturn(Optional.of(token));
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        passwordResetService.resetPassword(new ResetPasswordRequest("raw-token", "NewPassword123!"));

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("Credential"), eq(userId.toString()), type.capture(), payload.capture());
        assertThat(type.getValue()).isEqualTo(CredentialEvents.CREDENTIAL_PASSWORD_CHANGED);
        String json = new ObjectMapper().writeValueAsString(payload.getValue());
        assertThat(json).doesNotContain("NewPassword123!");
        assertThat(json).doesNotContain("raw-token");
    }
}
