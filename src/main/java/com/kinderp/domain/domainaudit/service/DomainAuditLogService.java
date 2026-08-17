package com.kinderp.domain.domainaudit.service;

import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditLog;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.domainaudit.repository.DomainAuditLogRepository;
import com.kinderp.domain.member.entity.Member;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainAuditLogService {

    private final DomainAuditLogRepository domainAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(Member actor,
                       Long kindergartenId,
                       DomainAuditAction action,
                       DomainAuditTargetType targetType,
                       Long targetId,
                       String summary,
                       Map<String, Object> metadata) {
        persist(kindergartenId,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getName() : null,
                actor != null ? actor.getRole() : null,
                action,
                targetType,
                targetId,
                summary,
                metadata);
    }

    @Transactional
    public void recordSystem(Long kindergartenId,
                             DomainAuditAction action,
                             DomainAuditTargetType targetType,
                             Long targetId,
                             String summary,
                             Map<String, Object> metadata) {
        persist(kindergartenId, null, "SYSTEM", null, action, targetType, targetId, summary, metadata);
    }

    private void persist(Long kindergartenId,
                         Long actorId,
                         String actorName,
                         com.kinderp.domain.member.entity.MemberRole actorRole,
                         DomainAuditAction action,
                         DomainAuditTargetType targetType,
                         Long targetId,
                         String summary,
                         Map<String, Object> metadata) {
        domainAuditLogRepository.save(DomainAuditLog.create(
                kindergartenId,
                actorId,
                normalize(actorName),
                actorRole,
                action,
                targetType,
                targetId,
                normalize(summary),
                toMetadataJson(metadata)
        ));
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize domain audit metadata", ex);
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
