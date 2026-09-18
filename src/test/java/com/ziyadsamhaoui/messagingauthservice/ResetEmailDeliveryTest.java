package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResetEmailDeliveryTest extends IntegrationTest {

    @Test
    void sendsResetEmailAndDeliversLink() {
        register("mailer@example.com");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "mailer@example.com")), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mailSender.sentCount()).isEqualTo(1);
    }

    @Test
    void secondRequestWithinCooldownIsRateLimited() {
        register("cooldown@example.com");
        restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "cooldown@example.com")), Void.class);

        ResponseEntity<Void> second = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "cooldown@example.com")), Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(mailSender.sentCount()).isEqualTo(1);
    }

    @Test
    void smtpFailureReturns502AndDoesNotConsumeCooldown() {
        register("smtpfail@example.com");
        mailSender.failNextSends();

        ResponseEntity<Void> failed = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "smtpfail@example.com")), Void.class);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        mailSender.succeed();
        ResponseEntity<Void> retry = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "smtpfail@example.com")), Void.class);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mailSender.sentCount()).isEqualTo(1);
    }

    @Test
    void repeatedFailuresOpenCircuitAndRejectBeforeContactingSmtp() {
        register("breaker@example.com");
        mailSender.failNextSends();

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    url("/auth/forgot-password"),
                    jsonEntity(Map.of("email", "breaker@example.com")), Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        ResponseEntity<Void> fourth = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "breaker@example.com")), Void.class);
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        Integer resetTokenCount = jdbcTemplate.queryForObject(
                "select count(*) from password_reset_tokens", Integer.class);
        assertThat(resetTokenCount).isEqualTo(3);
        mailSender.succeed();
    }

    @Test
    void unconfiguredSenderFailsClosed() {
        register("unconfigured@example.com");
        redisTemplate.delete("mail:breaker:open-until");
        redisTemplate.opsForValue().set("badrlink:mail:from-override", "");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                url("/auth/forgot-password"),
                jsonEntity(Map.of("email", "unconfigured@example.com")), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(mailSender.sentCount()).isZero();
    }
}
