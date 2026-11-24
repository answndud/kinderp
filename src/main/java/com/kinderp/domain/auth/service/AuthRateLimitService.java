package com.kinderp.domain.auth.service;

import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
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
    private static final String LOGIN_PAIR_KEY_PREFIX = "rate-limit:auth:login:pair:";
    private static final String REFRESH_IP_KEY_PREFIX = "rate-limit:auth:refresh:ip:";
    private static final String SIGNUP_IP_KEY_PREFIX = "rate-limit:auth:signup:ip:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuthRateLimitProperties properties;

    /**
     * 로그인 시도 전 한도 확인.
     * 이메일 단독으로 잠그면 남이 피해자 계정을 잠글 수 있으므로, 실패 횟수는 (IP+이메일) 쌍으로 센다.
     * 공격자 IP의 실패는 그 IP에서만 해당 계정을 막고, 피해자는 다른 IP에서 계속 로그인할 수 있다.
     * 여러 IP로 분산한 공격은 이메일 전체 기준의 큰 한도(loginEmailGlobalLimit)로 막는다.
     */
    public void validateLoginAllowed(String clientIp, String email) {
        assertUnderLimit(LOGIN_IP_KEY_PREFIX + normalizeClientIp(clientIp), properties.getLoginIpLimit());
        assertUnderLimit(loginPairKey(clientIp, email), properties.getLoginEmailLimit());
        assertUnderLimit(LOGIN_EMAIL_KEY_PREFIX + normalizeEmail(email), properties.getLoginEmailGlobalLimit());
    }

    public void recordLoginFailure(String clientIp, String email) {
        consumeSlot(
                LOGIN_IP_KEY_PREFIX + normalizeClientIp(clientIp),
                properties.getLoginIpLimit(),
                properties.getLoginWindow()
        );
        consumeSlot(
                loginPairKey(clientIp, email),
                properties.getLoginEmailLimit(),
                properties.getLoginWindow()
        );
        consumeSlot(
                LOGIN_EMAIL_KEY_PREFIX + normalizeEmail(email),
                properties.getLoginEmailGlobalLimit(),
                properties.getLoginWindow()
        );
    }

    /** 로그인에 성공하면 이 IP에서 쌓인 실패만 지운다. 다른 IP에서 쌓인 실패(공격 흔적)는 남겨 둔다. */
    public void clearLoginFailures(String clientIp, String email) {
        redisTemplate.delete(loginPairKey(clientIp, email));
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

    private String loginPairKey(String clientIp, String email) {
        return LOGIN_PAIR_KEY_PREFIX + normalizeClientIp(clientIp) + ":" + normalizeEmail(email);
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
