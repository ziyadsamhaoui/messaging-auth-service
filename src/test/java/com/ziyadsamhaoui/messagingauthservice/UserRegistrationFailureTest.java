package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserRegistrationFailureTest extends IntegrationTest {

    @Test
    void deletesCredentialWhenUserServiceFails() {
        userServer.failNextCalls();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/register"), jsonEntity(Map.of(
                        "email", "failed@example.com",
                        "username", "failed_user",
                        "password", "SecurePassword123!")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from credentials where email = ?",
                Integer.class, "failed@example.com");
        assertThat(count).isZero();
        assertThat(userServer.callCount()).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejectedBeforeUserCall() {
        register("dup@example.com");
        int callsAfterFirst = userServer.callCount();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/register"), jsonEntity(Map.of(
                        "email", "dup@example.com",
                        "username", "dup_user",
                        "password", "SecurePassword123!")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userServer.callCount()).isEqualTo(callsAfterFirst);
    }
}
