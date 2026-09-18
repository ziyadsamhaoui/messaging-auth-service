package com.ziyadsamhaoui.messagingauthservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtDenylistService {

    private static final String KEY_PREFIX = "jwt:denylist:";

    private final StringRedisTemplate redisTemplate;

    public void deny(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "revoked", ttl);
        } catch (RuntimeException ex) {
            log.error("failed to store jti in redis denylist", ex);
        }
    }

    public boolean isDenied(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (RuntimeException ex) {
            log.error("failed to check redis denylist", ex);
            return false;
        }
    }
}
