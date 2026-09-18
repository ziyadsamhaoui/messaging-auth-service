package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTest extends IntegrationTest {

    @Test
    void resetTokenIsSingleUseAndExpires() {
        register("reset@example.com");

        ResponseEntity<Map> loginBefore = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "reset@example.com",
                        "password", "SecurePassword123!")), Map.class);
        assertThat(loginBefore.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> forgot = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "reset@example.com")), Void.class);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.OK);

        String rawToken = fetchLatestResetTokenRaw();

        ResponseEntity<Void> reset = restTemplate.postForEntity(
                url("/auth/reset-password"),
                jsonEntity(Map.of("token", rawToken, "newPassword", "NewSecurePassword456!")), Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> replay = restTemplate.postForEntity(
                url("/auth/reset-password"),
                jsonEntity(Map.of("token", rawToken, "newPassword", "AnotherPassword789!")), Void.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> loginOld = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "reset@example.com",
                        "password", "SecurePassword123!")), Map.class);
        assertThat(loginOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> loginNew = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "reset@example.com",
                        "password", "NewSecurePassword456!")), Map.class);
        assertThat(loginNew.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredResetTokenIsRejected() {
        register("expiredreset@example.com");
        restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "expiredreset@example.com")), Void.class);
        String rawToken = fetchLatestResetTokenRaw();

        jdbcTemplate.update(
                "update password_reset_tokens set expiry_date = now() - interval '1 minute'");

        ResponseEntity<Void> reset = restTemplate.postForEntity(
                url("/auth/reset-password"),
                jsonEntity(Map.of("token", rawToken, "newPassword", "WhateverPassword123!")), Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgotPasswordForUnknownEmailReturnsOkWithoutCreatingToken() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "ghost@example.com")), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer tokenCount = jdbcTemplate.queryForObject(
                "select count(*) from password_reset_tokens", Integer.class);
        assertThat(tokenCount).isZero();
    }

}