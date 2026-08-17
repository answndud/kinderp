package com.kinderp.domain.authaudit.service;

import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditLog;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.authaudit.repository.AuthAuditLogRepository;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class AuthAuditLogService {

    private final AuthAuditLogRepository authAuditLogRepository;
    private final AuthAuditMetricsService authAuditMetricsService;
    private final MemberRepository memberRepository;

    public void recordLoginSuccess(Long memberId, String email, MemberAuthProvider provider, String clientIp) {
        saveAuditLog(memberId, email, provider, AuthAuditEventType.LOGIN, AuthAuditResult.SUCCESS, null, clientIp);
    }

    public void recordLoginFailure(String email, MemberAuthProvider provider, String clientIp, String reason) {
        saveAuditLog(null, email, provider, AuthAuditEventType.LOGIN, AuthAuditResult.FAILURE, reason, clientIp);
    }

    public void recordRefreshSuccess(Long memberId, String email, String clientIp) {
        saveAuditLog(memberId, email, null, AuthAuditEventType.REFRESH, AuthAuditResult.SUCCESS, null, clientIp);
    }

    public void recordRefreshFailure(Long memberId, String email, String clientIp, String reason) {
        saveAuditLog(memberId, email, null, AuthAuditEventType.REFRESH, AuthAuditResult.FAILURE, reason, clientIp);
    }

    public void recordSocialLinkSuccess(Long memberId, String email, MemberAuthProvider provider, String clientIp) {
        saveAuditLog(memberId, email, provider, AuthAuditEventType.SOCIAL_LINK, AuthAuditResult.SUCCESS, null, clientIp);
    }

    public void recordSocialLinkFailure(Long memberId, String email, MemberAuthProvider provider, String clientIp, String reason) {
        saveAuditLog(memberId, email, provider, AuthAuditEventType.SOCIAL_LINK, AuthAuditResult.FAILURE, reason, clientIp);
    }

    public void recordSocialUnlinkSuccess(Long memberId, String email, MemberAuthProvider provider, String clientIp) {
        saveAuditLog(memberId, email, provider, AuthAuditEventType.SOCIAL_UNLINK, AuthAuditResult.SUCCESS, null, clientIp);
    }

    public void recordSocialUnlinkFailure(Long memberId, String email, MemberAuthProvider provider, String clientIp, String reason) {
        saveAuditLog(memberId, email, provider, AuthAuditEventType.SOCIAL_UNLINK, AuthAuditResult.FAILURE, reason, clientIp);
    }

    private void saveAuditLog(Long memberId,
                              String email,
                              MemberAuthProvider provider,
                              AuthAuditEventType eventType,
                              AuthAuditResult result,
                              String reason,
                              String clientIp) {
        authAuditMetricsService.record(eventType, result, provider);

        try {
            authAuditLogRepository.save(AuthAuditLog.create(
                    memberId,
                    resolveKindergartenId(memberId, email),
                    normalizeEmail(email),
                    provider,
                    eventType,
                    result,
                    normalizeReason(reason),
                    normalizeClientIp(clientIp)
            ));
        } catch (Exception ex) {
            log.warn("Failed to persist auth audit log eventType={} result={} memberId={}",
                    eventType, result, memberId, ex);
        }
    }

    private Long resolveKindergartenId(Long memberId, String email) {
        if (memberId != null) {
            return memberRepository.findKindergartenIdById(memberId).orElse(null);
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return null;
        }
        return memberRepository.findKindergartenIdByEmail(normalizedEmail).orElse(null);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        return clientIp.trim();
    }
}
