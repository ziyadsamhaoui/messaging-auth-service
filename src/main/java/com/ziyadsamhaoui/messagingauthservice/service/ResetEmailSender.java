package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.exception.EmailDeliveryException;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResetEmailSender {

    private static final String BREAKER_KEY = "mail:breaker:open-until";
    private static final String BREAKER_FAILURES_KEY = "mail:breaker:consecutive-failures";
    private static final String FROM_OVERRIDE_KEY = "badrlink:mail:from-override";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final Semaphore sendPermits = new Semaphore(2);

    public void assertAvailable() {
        assertBreakerClosed();
    }

    public void sendResetEmail(String to, String rawToken) {
        assertBreakerClosed();
        if (!sendPermits.tryAcquire()) {
            throw new EmailDeliveryException("reset email service is busy, try again shortly");
        }
        try {
            doSend(to, rawToken);
        } finally {
            sendPermits.release();
        }
    }

    private void assertBreakerClosed() {
        String openUntil = redisTemplate.opsForValue().get(BREAKER_KEY);
        if (openUntil != null) {
            throw new EmailDeliveryException(
                    "email delivery is temporarily unavailable, try again after " + openUntil);
        }
    }

    private void doSend(String to, String rawToken) {
        String from = resolveFromAddress();
        if (from == null || from.isBlank()) {
            throw new EmailDeliveryException("email sender is not configured");
        }
        String resetLink = "/auth/reset-password?token=" + rawToken;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("BadrLink password reset");
            helper.setText("""
                    You requested a password reset for your BadrLink account.

                    Open this link to choose a new password (valid for 1 hour):

                    %s

                    If you did not request this, you can safely ignore this email.
                    """.formatted(resetLink), false);
            mailSender.send(message);
            redisTemplate.opsForValue().set(BREAKER_FAILURES_KEY, "0");
        } catch (MessagingException | org.springframework.mail.MailException ex) {
            registerFailure(ex);
            throw new EmailDeliveryException("failed to send reset email", ex);
        }
    }

    private String resolveFromAddress() {
        try {
            String override = redisTemplate.opsForValue().get(FROM_OVERRIDE_KEY);
            if (override != null) {
                return override;
            }
        } catch (RuntimeException ex) {
            log.error("failed to read email from-override, using configured sender", ex);
        }
        return properties.email().from();
    }

    private void registerFailure(Throwable cause) {
        AuthProperties.Email email = properties.email();
        try {
            Long failures = redisTemplate.opsForValue().increment(BREAKER_FAILURES_KEY);
            if (failures != null && failures >= email.failureThreshold()) {
                Instant openUntil = Instant.now().plus(email.failureCooldown());
                redisTemplate.opsForValue().set(BREAKER_KEY, openUntil.toString(), email.failureCooldown());
                redisTemplate.delete(BREAKER_FAILURES_KEY);
                log.error("reset email circuit opened until {}", openUntil, cause);
            }
        } catch (RuntimeException redisError) {
            log.error("failed to update email circuit state", redisError);
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            redisTemplate.delete(BREAKER_KEY);
            redisTemplate.delete(BREAKER_FAILURES_KEY);
        } catch (RuntimeException ignored) {
        }
    }
}
