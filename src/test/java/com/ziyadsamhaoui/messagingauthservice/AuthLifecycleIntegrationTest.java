package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLifecycleIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginRefreshLogoutWorkflow() {
        Map<String, String> registerBody = Map.of(
                "email", "lifecycle@example.com",
                "username", "lifecycle_user",
                "password", "SecurePassword123!");
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
                url("/auth/register"), jsonEntity(registerBody), Map.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "lifecycle@example.com",
                        "password", "SecurePassword123!")), Map.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        String refreshToken = (String) loginResponse.getBody().get("refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", refreshToken)), Map.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefreshToken = (String) refreshResponse.getBody().get("refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                url("/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", newRefreshToken), headers), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> reusedRefresh = restTemplate.postForEntity(
                url("/auth/refresh"), jsonEntity(Map.of("refreshToken", refreshToken)), Map.class);
        assertThat(reusedRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Integer revokedCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = (select id from credentials where email = ?) and is_revoked = true",
                Integer.class, "lifecycle@example.com");
        assertThat(revokedCount).isGreaterThanOrEqualTo(2);
    }
}
