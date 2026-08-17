package com.kinderp.domain.authaudit.dto.response;

import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditLog;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.member.entity.MemberAuthProvider;

import java.time.LocalDateTime;

public record AuthAuditLogResponse(
        Long id,
        Long memberId,
        String email,
        MemberAuthProvider provider,
        AuthAuditEventType eventType,
        AuthAuditResult result,
        String reason,
        String clientIp,
        LocalDateTime createdAt
) {

    public static AuthAuditLogResponse from(AuthAuditLog authAuditLog) {
        return new AuthAuditLogResponse(
                authAuditLog.getId(),
                authAuditLog.getMemberId(),
                authAuditLog.getEmail(),
                authAuditLog.getProvider(),
                authAuditLog.getEventType(),
                authAuditLog.getResult(),
                authAuditLog.getReason(),
                authAuditLog.getClientIp(),
                authAuditLog.getCreatedAt()
        );
    }
}
