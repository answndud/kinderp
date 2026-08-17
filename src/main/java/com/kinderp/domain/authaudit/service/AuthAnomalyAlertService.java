package com.kinderp.domain.authaudit.service;

import com.kinderp.domain.authaudit.config.AuthAlertProperties;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthAnomalyAlertService {

    private static final String LOGIN_FAILURE_COUNT_KEY_PREFIX = "auth-alert:login-failure:count:";
    private static final String LOGIN_FAILURE_COOLDOWN_KEY_PREFIX = "auth-alert:login-failure:cooldown:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final AuthAlertProperties authAlertProperties;

    public void alertRepeatedLoginFailuresIfNeeded(String email, String clientIp) {
        if (!authAlertProperties.isEnabled()) {
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return;
        }

        try {
            memberRepository.findByEmail(normalizedEmail)
                    .ifPresent(member -> maybeSendLoginFailureAlert(member, normalizedEmail, normalizeClientIp(clientIp)));
        } catch (Exception ex) {
            log.warn("Failed to evaluate auth anomaly alert", ex);
        }
    }

    public void clearLoginFailureCounter(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return;
        }

        try {
            memberRepository.findByEmail(normalizedEmail)
                    .ifPresent(member -> {
                        if (member.getKindergarten() == null) {
                            return;
                        }
                        redisTemplate.delete(LOGIN_FAILURE_COUNT_KEY_PREFIX + member.getKindergarten().getId() + ":" + normalizedEmail);
                    });
        } catch (Exception ex) {
            log.warn("Failed to clear auth anomaly counter", ex);
        }
    }

    private void maybeSendLoginFailureAlert(Member member, String normalizedEmail, String clientIp) {
        if (member.getKindergarten() == null) {
            return;
        }

        long threshold = Math.max(authAlertProperties.getLoginFailureThreshold(), 1L);
        Duration window = normalizeDuration(authAlertProperties.getLoginFailureWindow(), Duration.ofMinutes(10));
        Duration cooldown = normalizeDuration(authAlertProperties.getAlertCooldown(), Duration.ofMinutes(30));
        String countKey = LOGIN_FAILURE_COUNT_KEY_PREFIX + member.getKindergarten().getId() + ":" + normalizedEmail;
        String cooldownKey = LOGIN_FAILURE_COOLDOWN_KEY_PREFIX + member.getKindergarten().getId() + ":" + normalizedEmail;

        Long failures = redisTemplate.opsForValue().increment(countKey);
        if (failures != null && failures == 1L) {
            redisTemplate.expire(countKey, window);
        }

        if (failures == null || failures < threshold) {
            return;
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            return;
        }

        List<Long> principalIds = memberRepository.findAllByKindergartenIdAndRoles(
                        member.getKindergarten().getId(),
                        List.of(MemberRole.PRINCIPAL)
                ).stream()
                .map(Member::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (principalIds.isEmpty()) {
            return;
        }

        String title = "인증 이상 징후 감지";
        String content = "%s 계정에서 %d분 내 로그인 실패가 %d회 이상 발생했습니다. 최근 IP: %s"
                .formatted(
                        normalizedEmail,
                        Math.max(window.toMinutes(), 1L),
                        threshold,
                        clientIp == null ? "-" : clientIp
                );

        notificationService.notifyWithLink(
                principalIds,
                NotificationType.AUTH_ANOMALY_DETECTED,
                title,
                content,
                buildAuditLogLink(normalizedEmail)
        );

        redisTemplate.opsForValue().set(cooldownKey, "sent", cooldown);
    }

    private String buildAuditLogLink(String normalizedEmail) {
        return "/audit-logs?eventType=LOGIN&result=FAILURE&email="
                + UriUtils.encodeQueryParam(normalizedEmail, StandardCharsets.UTF_8);
    }

    private Duration normalizeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        return clientIp.trim();
    }
}
