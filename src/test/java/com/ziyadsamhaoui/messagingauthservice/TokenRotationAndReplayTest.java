package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRotationAndReplayTest extends IntegrationTest {

    @Test
    void reusingRotatedRefreshTokenRevokesEntireChain() {
        register("replay@example.com");
        ResponseEntity<Map> login = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "replay@example.com",
                        "password", "SecurePassword123!")), Map.class);
        String refreshToken = (String) login.getBody().get("refreshToken");

        ResponseEntity<Map> firstRefresh = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", refreshToken)), Map.class);
        assertThat(firstRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedToken = (String) firstRefresh.getBody().get("refreshToken");
        assertThat(rotatedToken).isNotEqualTo(refreshToken);

        ResponseEntity<Map> replay = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", refreshToken)), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Integer revokedCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = (select id from credentials where email = ?) and is_revoked = true",
                Integer.class, "replay@example.com");
        assertThat(revokedCount).isEqualTo(2);

        ResponseEntity<Map> rotatedAfterReplay = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", rotatedToken)), Map.class);
        assertThat(rotatedAfterReplay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        register("unknown@example.com");
        String fakeToken = UUID.randomUUID().toString();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", fakeToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredRefreshTokenIsRejectedAndMarkedExpired() {
        register("expired@example.com");
        ResponseEntity<Map> login = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "expired@example.com",
                        "password", "SecurePassword123!")), Map.class);
        String refreshToken = (String) login.getBody().get("refreshToken");

        jdbcTemplate.update(
                "update refresh_tokens set expiry_date = now() - interval '1 minute' where token_hash = (select token_hash from refresh_tokens order by id desc limit 1)");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", refreshToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Integer expiredCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where is_expired = true",
                Integer.class);
        assertThat(expiredCount).isEqualTo(1);
    }
}
