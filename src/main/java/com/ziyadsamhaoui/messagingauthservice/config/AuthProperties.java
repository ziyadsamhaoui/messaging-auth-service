package com.ziyadsamhaoui.messagingauthservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "badrlink.security")
public record AuthProperties(Jwt jwt, Lockout lockout, PasswordReset passwordReset, Email email) {

    public AuthProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null, null);
        }
        if (lockout == null) {
            lockout = new Lockout(5, Duration.ofMinutes(15));
        }
        if (passwordReset == null) {
            passwordReset = new PasswordReset(Duration.ofHours(1));
        }
        if (email == null) {
            email = new Email(null, Duration.ofMinutes(2), 20, 3, Duration.ofMinutes(5));
        }
    }

    public record Jwt(String hmacSecret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Lockout(int maxFailedAttempts, Duration duration) {
    }

    public record PasswordReset(Duration tokenTtl) {
    }

    public record Email(String from, Duration resetCooldown, int hourlyLimit,
                        int failureThreshold, Duration failureCooldown) {
    }
}
