package com.ziyadsamhaoui.messagingauthservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public final class AuthResponses {

    private AuthResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds
    ) {
        public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
        }
    }

    public record RefreshedTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds
    ) {
        public static RefreshedTokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
            return new RefreshedTokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
        }
    }

    public record RegisterResponse(
            UUID id,
            String email
    ) {}

    public record LockoutStatus(
            boolean locked,
            Instant lockoutEnd
    ) {}
}
