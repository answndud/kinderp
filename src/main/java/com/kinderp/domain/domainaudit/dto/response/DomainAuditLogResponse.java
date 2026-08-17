package com.kinderp.domain.domainaudit.dto.response;

import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditLog;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.member.entity.MemberRole;

import java.time.LocalDateTime;

public record DomainAuditLogResponse(
        Long id,
        Long kindergartenId,
        Long actorId,
        String actorName,
        MemberRole actorRole,
        DomainAuditAction action,
        DomainAuditTargetType targetType,
        Long targetId,
        String summary,
        String metadataJson,
        LocalDateTime createdAt
) {
    public static DomainAuditLogResponse from(DomainAuditLog log) {
        return new DomainAuditLogResponse(
                log.getId(),
                log.getKindergartenId(),
                log.getActorId(),
                log.getActorName(),
                log.getActorRole(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getSummary(),
                log.getMetadataJson(),
                log.getCreatedAt()
        );
    }
}
