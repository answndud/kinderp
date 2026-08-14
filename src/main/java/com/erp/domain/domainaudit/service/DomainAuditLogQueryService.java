package com.erp.domain.domainaudit.service;

import com.erp.domain.domainaudit.dto.response.DomainAuditLogResponse;
import com.erp.domain.domainaudit.entity.DomainAuditAction;
import com.erp.domain.domainaudit.entity.DomainAuditLog;
import com.erp.domain.domainaudit.entity.DomainAuditTargetType;
import com.erp.domain.domainaudit.repository.DomainAuditLogRepository;
import com.erp.domain.member.repository.MemberRepository;
import com.erp.global.common.PageRequests;
import com.erp.global.common.ProductTime;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DomainAuditLogQueryService {

    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_EXPORT_DAYS = 31;

    private final DomainAuditLogRepository domainAuditLogRepository;
    private final MemberRepository memberRepository;

    public Page<DomainAuditLogResponse> getAuditLogsForPrincipal(Long principalId,
                                                                 DomainAuditAction action,
                                                                 DomainAuditTargetType targetType,
                                                                 String actorName,
                                                                 String summary,
                                                                 LocalDate from,
                                                                 LocalDate to,
                                                                 int page,
                                                                 int size) {
        Long kindergartenId = resolvePrincipalKindergartenId(principalId);
        return domainAuditLogRepository.searchByKindergartenId(
                        kindergartenId,
                        action,
                        targetType,
                        normalize(actorName),
                        normalize(summary),
                        from != null ? from.atStartOfDay() : null,
                        to != null ? to.plusDays(1).atStartOfDay() : null,
                        PageRequests.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                )
                .map(DomainAuditLogResponse::from);
    }

    public byte[] exportAuditLogsCsvForPrincipal(Long principalId,
                                                 DomainAuditAction action,
                                                 DomainAuditTargetType targetType,
                                                 String actorName,
                                                 String summary,
                                                 LocalDate from,
                                                 LocalDate to) {
        Long kindergartenId = resolvePrincipalKindergartenId(principalId);
        LocalDate exportTo = to != null ? to : ProductTime.today();
        LocalDate exportFrom = from != null ? from : exportTo.minusDays(MAX_EXPORT_DAYS - 1L);
        if (exportFrom.isAfter(exportTo) || ChronoUnit.DAYS.between(exportFrom, exportTo) >= MAX_EXPORT_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "감사 로그 export 기간은 최대 31일입니다");
        }

        List<DomainAuditLog> logs = domainAuditLogRepository.searchAllByKindergartenId(
                kindergartenId,
                action,
                targetType,
                normalize(actorName),
                normalize(summary),
                exportFrom.atStartOfDay(),
                exportTo.plusDays(1).atStartOfDay(),
                org.springframework.data.domain.PageRequest.of(0, MAX_EXPORT_ROWS,
                        Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        );

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("createdAt,action,targetType,targetId,actorName,actorRole,summary,metadataJson\n");
        for (DomainAuditLog log : logs) {
            csv.append(toCsv(log.getCreatedAt()))
                    .append(',').append(toCsv(log.getAction()))
                    .append(',').append(toCsv(log.getTargetType()))
                    .append(',').append(toCsv(log.getTargetId()))
                    .append(',').append(toCsv(log.getActorName()))
                    .append(',').append(toCsv(log.getActorRole()))
                    .append(',').append(toCsv(log.getSummary()))
                    .append(',').append(toCsv(log.getMetadataJson()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Long resolvePrincipalKindergartenId(Long principalId) {
        return memberRepository.findKindergartenIdById(principalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toCsv(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }
}
