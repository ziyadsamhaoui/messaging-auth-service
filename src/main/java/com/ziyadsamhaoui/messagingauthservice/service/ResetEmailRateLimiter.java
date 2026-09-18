package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.exception.EmailDeliveryException;
import com.ziyadsamhaoui.messagingauthservice.exception.EmailRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResetEmailRateLimiter {

    private static final String COOLDOWN_KEY_PREFIX = "mail:reset:cooldown:";
    private static final String QUOTA_KEY_PREFIX = "mail:reset:quota:";

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local cooldown = redis.call('exists', KEYS[1])
            if cooldown == 1 then
                return -1
            end
            local quota = tonumber(redis.call('get', KEYS[2]) or '0')
            if quota >= tonumber(ARGV[1]) then
                return -2
            end
            redis.call('incr', KEYS[2])
            if quota == 0 then
                redis.call('pexpire', KEYS[2], ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public void consumeSlot(String email) {
        AuthProperties.Email emailProps = properties.email();
        long windowMillis = Duration.ofHours(1).toMillis();
        String cooldownKey = COOLDOWN_KEY_PREFIX + email.toLowerCase();
        String quotaKey = QUOTA_KEY_PREFIX + email.toLowerCase();
        Long result;
        try {
            result = redisTemplate.execute(CONSUME_SCRIPT,
                    List.of(cooldownKey, quotaKey),
                    String.valueOf(emailProps.hourlyLimit()),
                    String.valueOf(windowMillis));
        } catch (RuntimeException ex) {
            log.error("reset email limiter backend unavailable", ex);
            throw new EmailDeliveryException("email limiter unavailable, reset not processed");
        }
        if (result == null) {
            throw new EmailDeliveryException("email limiter unavailable, reset not processed");
        }
        if (result == -1) {
            throw new EmailRateLimitException(
                    "a reset email was sent recently, retry after the cooldown window");
        }
        if (result == -2) {
            throw new EmailRateLimitException("too many reset emails requested, try again later");
        }
    }

    public void markSent(String email) {
        try {
            redisTemplate.opsForValue().set(COOLDOWN_KEY_PREFIX + email.toLowerCase(),
                    "1", properties.email().resetCooldown());
        } catch (RuntimeException ex) {
            log.error("failed to persist reset email cooldown", ex);
        }
    }
}
