package com.erp.domain.auth.service;

import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 인증 API 남용을 막기 위한 단순 Redis 기반 rate limit 서비스
 */
@Service
@RequiredArgsConstructor
public class AuthRateLimitService {

    private static final String LOGIN_IP_KEY_PREFIX = "rate-limit:auth:login:ip:";
    private static final String LOGIN_EMAIL_KEY_PREFIX = "rate-limit:auth:login:email:";
    private static final String REFRESH_IP_KEY_PREFIX = "rate-limit:auth:refresh:ip:";
    private static final String SIGNUP_IP_KEY_PREFIX = "rate-limit:auth:signup:ip:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuthRateLimitProperties properties;

    public void validateLoginAllowed(String clientIp, String email) {
        assertUnderLimit(LOGIN_IP_KEY_PREFIX + normalizeClientIp(clientIp), properties.getLoginIpLimit());
        assertUnderLimit(LOGIN_EMAIL_KEY_PREFIX + normalizeEmail(email), properties.getLoginEmailLimit());
    }

    public void recordLoginFailure(String clientIp, String email) {
        consumeSlot(
                LOGIN_IP_KEY_PREFIX + normalizeClientIp(clientIp),
                properties.getLoginIpLimit(),
                properties.getLoginWindow()
        );
        consumeSlot(
                LOGIN_EMAIL_KEY_PREFIX + normalizeEmail(email),
                properties.getLoginEmailLimit(),
                properties.getLoginWindow()
        );
    }

    public void clearLoginFailures(String email) {
        redisTemplate.delete(LOGIN_EMAIL_KEY_PREFIX + normalizeEmail(email));
    }

    public void validateRefreshAllowed(String clientIp) {
        consumeSlot(
                REFRESH_IP_KEY_PREFIX + normalizeClientIp(clientIp),
                properties.getRefreshIpLimit(),
                properties.getRefreshWindow()
        );
    }

    public void validateSignupAllowed(String clientIp) {
        consumeSlot(
                SIGNUP_IP_KEY_PREFIX + normalizeClientIp(clientIp),
                properties.getSignupIpLimit(),
                properties.getSignupWindow()
        );
    }

    private void consumeSlot(String key, long limit, Duration window) {
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, window);
        }

        if (attempts != null && attempts > limit) {
            throw new BusinessException(ErrorCode.AUTH_RATE_LIMITED);
        }
    }

    private void assertUnderLimit(String key, long limit) {
        Long attempts = readAttempts(key);
        if (attempts != null && attempts >= limit) {
            throw new BusinessException(ErrorCode.AUTH_RATE_LIMITED);
        }
    }

    private Long readAttempts(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "anonymous";
        }
        return email.trim().toLowerCase();
    }
}
