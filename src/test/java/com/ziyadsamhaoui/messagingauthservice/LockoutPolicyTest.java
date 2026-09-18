package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LockoutPolicyTest extends IntegrationTest {

    @Test
    void locksAccountAfterFiveFailedLoginsAndUnlocksAfterWindow() {
        register("locked@example.com", "SecurePassword123!");

        ResponseEntity<Map> lastResponse = null;
        for (int i = 0; i < 5; i++) {
            lastResponse = restTemplate.postForEntity(
                    url("/auth/login"), jsonEntity(Map.of(
                            "email", "locked@example.com",
                            "password", "WrongPassword1!")), Map.class);
            assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map> lockedResponse = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "locked@example.com",
                        "password", "SecurePassword123!")), Map.class);
        assertThat(lockedResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        Integer failedAttempts = jdbcTemplate.queryForObject(
                "select failed_login_attempts from credentials where email = ?",
                Integer.class, "locked@example.com");
        assertThat(failedAttempts).isEqualTo(5);

        Integer isLocked = jdbcTemplate.queryForObject(
                "select case when is_locked then 1 else 0 end from credentials where email = ?",
                Integer.class, "locked@example.com");
        assertThat(isLocked).isEqualTo(1);

        jdbcTemplate.update(
                "update credentials set lockout_end = now() - interval '1 minute' where email = ?",
                "locked@example.com");

        AtomicReference<ResponseEntity<Map>> unlockedResponse = new AtomicReference<>();
        unlockedResponse.set(restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "locked@example.com",
                        "password", "SecurePassword123!")), Map.class));
        assertThat(unlockedResponse.get().getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer resetAttempts = jdbcTemplate.queryForObject(
                "select failed_login_attempts from credentials where email = ?",
                Integer.class, "locked@example.com");
        assertThat(resetAttempts).isZero();
    }

    @Test
    void failedAttemptWhileLockedDoesNotExtendLockout() {
        register("stilllocked@example.com", "SecurePassword123!");
        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity(
                    url("/auth/login"), jsonEntity(Map.of(
                            "email", "stilllocked@example.com",
                            "password", "WrongPassword1!")), Map.class);
        }
        ResponseEntity<Map> whileLocked = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "stilllocked@example.com",
                        "password", "WrongPassword1!")), Map.class);
        assertThat(whileLocked.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }
}
