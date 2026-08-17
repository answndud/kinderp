package com.kinderp.domain.kidapplication.service;

import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.domainaudit.service.DomainAuditLogService;
import com.kinderp.domain.kidapplication.entity.KidApplication;
import com.kinderp.domain.member.entity.Member;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KidApplicationAuditService {

    private final DomainAuditLogService domainAuditLogService;

    public void record(Member actor,
                       DomainAuditAction action,
                       String summary,
                       Map<String, Object> metadata,
                       KidApplication application) {
        domainAuditLogService.record(
                actor,
                application.getKindergarten().getId(),
                action,
                DomainAuditTargetType.KID_APPLICATION,
                application.getId(),
                summary,
                metadata
        );
    }

    public void recordSystemOfferExpired(KidApplication application, String offerExpiresAt) {
        domainAuditLogService.recordSystem(
                application.getKindergarten().getId(),
                DomainAuditAction.KID_APPLICATION_OFFER_EXPIRED,
                DomainAuditTargetType.KID_APPLICATION,
                application.getId(),
                application.getKidName() + "의 입학 제안이 만료되었습니다.",
                Map.of("offerExpiresAt", offerExpiresAt)
        );
    }
}
