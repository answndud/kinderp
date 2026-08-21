package com.kinderp.domain.notification.service;

import com.kinderp.domain.notification.config.NotificationRateLimitProperties;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** 사용자/IP 기준으로 외부 알림 enqueue 요청을 제한한다. */
@Service
@RequiredArgsConstructor
public class NotificationRateLimitService {

    private static final String USER_KEY_PREFIX = "rate-limit:notification:create:user:";
    private static final String IP_KEY_PREFIX = "rate-limit:notification:create:ip:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationRateLimitProperties properties;

    public void validateCreateAllowed(Long memberId, String clientIp) {
        consumeSlot(USER_KEY_PREFIX + normalizeMemberId(memberId), properties.getUserLimit());
        consumeSlot(IP_KEY_PREFIX + normalizeClientIp(clientIp), properties.getIpLimit());
    }

    private void consumeSlot(String key, long limit) {
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            Duration window = properties.getWindow();
            redisTemplate.expire(key, window);
        }

        if (attempts != null && attempts > limit) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_RATE_LIMITED);
        }
    }

    private String normalizeMemberId(Long memberId) {
        return memberId == null ? "anonymous" : memberId.toString();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }
}
