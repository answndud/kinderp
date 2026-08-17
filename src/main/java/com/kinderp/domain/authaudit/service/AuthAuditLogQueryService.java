package com.kinderp.domain.authaudit.service;

import com.kinderp.domain.authaudit.dto.response.AuthAuditLogResponse;
import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditLog;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.authaudit.repository.AuthAuditLogRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.common.PageRequests;
import com.kinderp.global.common.ProductTime;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthAuditLogQueryService {

    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_EXPORT_DAYS = 31;

    private final AuthAuditLogRepository authAuditLogRepository;
    private final MemberService memberService;

    public Page<AuthAuditLogResponse> getAuditLogsForPrincipal(Long requesterId,
                                                               AuthAuditEventType eventType,
                                                               AuthAuditResult result,
                                                               MemberAuthProvider provider,
                                                               String email,
                                                               String reason,
                                                               LocalDate from,
                                                               LocalDate to,
                                                               int page,
                                                               int size) {
        Member requester = memberService.getMemberByIdWithKindergarten(requesterId);
        validateRequester(requester);

        PageRequest pageRequest = PageRequests.pageRequest(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        return authAuditLogRepository.searchByKindergartenId(
                requester.getKindergarten().getId(),
                eventType,
                result,
                provider,
                normalizeEmailKeyword(email),
                normalizeKeyword(reason),
                atStartOfDay(from),
                toExclusive(to),
                pageRequest
        ).map(AuthAuditLogResponse::from);
    }

    public byte[] exportAuditLogsCsvForPrincipal(Long requesterId,
                                                 AuthAuditEventType eventType,
                                                 AuthAuditResult result,
                                                 MemberAuthProvider provider,
                                                 String email,
                                                 String reason,
                                                 LocalDate from,
                                                 LocalDate to) {
        Member requester = memberService.getMemberByIdWithKindergarten(requesterId);
        validateRequester(requester);

        LocalDate exportTo = resolveExportTo(to);
        LocalDate exportFrom = resolveExportFrom(from, exportTo);
        List<AuthAuditLog> logs = authAuditLogRepository.searchAllByKindergartenId(
                requester.getKindergarten().getId(),
                eventType,
                result,
                provider,
                normalizeEmailKeyword(email),
                normalizeKeyword(reason),
                atStartOfDay(exportFrom),
                toExclusive(exportTo),
                org.springframework.data.domain.PageRequest.of(0, MAX_EXPORT_ROWS,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );

        return toCsv(logs).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void validateRequester(Member requester) {
        if (requester.getRole() != MemberRole.PRINCIPAL || requester.getKindergarten() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private String normalizeEmailKeyword(String email) {
        return normalizeKeyword(email);
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private LocalDateTime atStartOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay();
    }

    private LocalDate resolveExportTo(LocalDate to) {
        return to != null ? to : ProductTime.today();
    }

    private LocalDate resolveExportFrom(LocalDate from, LocalDate to) {
        LocalDate resolved = from != null ? from : to.minusDays(MAX_EXPORT_DAYS - 1L);
        if (resolved.isAfter(to) || ChronoUnit.DAYS.between(resolved, to) >= MAX_EXPORT_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "감사 로그 export 기간은 최대 31일입니다");
        }
        return resolved;
    }

    private LocalDateTime toExclusive(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay();
    }

    private String toCsv(List<AuthAuditLog> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("createdAt,eventType,result,email,provider,reason,clientIp,memberId\n");

        for (AuthAuditLog log : logs) {
            csv.append(csvEscape(log.getCreatedAt()))
                    .append(',')
                    .append(csvEscape(log.getEventType()))
                    .append(',')
                    .append(csvEscape(log.getResult()))
                    .append(',')
                    .append(csvEscape(log.getEmail()))
                    .append(',')
                    .append(csvEscape(log.getProvider()))
                    .append(',')
                    .append(csvEscape(log.getReason()))
                    .append(',')
                    .append(csvEscape(log.getClientIp()))
                    .append(',')
                    .append(csvEscape(log.getMemberId()))
                    .append('\n');
        }

        return csv.toString();
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }

        String raw = String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
