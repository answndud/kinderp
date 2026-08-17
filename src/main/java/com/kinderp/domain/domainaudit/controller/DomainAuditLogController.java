package com.kinderp.domain.domainaudit.controller;

import com.kinderp.domain.domainaudit.dto.response.DomainAuditLogResponse;
import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.domainaudit.service.DomainAuditLogQueryService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.common.ProductTime;
import com.kinderp.global.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/domain-audit-logs")
@RequiredArgsConstructor
@Tag(name = "Domain Audit", description = "원장 전용 업무 상태 변경 이력 조회/CSV export API")
public class DomainAuditLogController {

    private final DomainAuditLogQueryService domainAuditLogQueryService;

    @GetMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "업무 감사 로그 조회",
            description = "입학, 출결 요청, 공지 등 주요 업무 상태 변경 이력을 유치원 단위로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 감사 로그 page 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "content": [
                                          {
                                            "action": "KID_APPLICATION_OFFERED",
                                            "targetType": "KID_APPLICATION",
                                            "actorName": "김원장",
                                            "summary": "입학 제안 발송 샘플: 서아"
                                          }
                                        ],
                                        "totalElements": 1,
                                        "number": 0
                                      }
                                    }
                                    """)
                    )
            )
    )
    public ResponseEntity<ApiResponse<Page<DomainAuditLogResponse>>> getAuditLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DomainAuditAction action,
            @RequestParam(required = false) DomainAuditTargetType targetType,
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<DomainAuditLogResponse> responses = domainAuditLogQueryService.getAuditLogsForPrincipal(
                userDetails.getMemberId(),
                action,
                targetType,
                actorName,
                summary,
                from,
                to,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(summary = "업무 감사 로그 CSV export", description = "현재 필터 조건에 맞는 업무 감사 로그를 CSV 파일로 내려받습니다.")
    public ResponseEntity<byte[]> exportAuditLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DomainAuditAction action,
            @RequestParam(required = false) DomainAuditTargetType targetType,
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        byte[] csv = domainAuditLogQueryService.exportAuditLogsCsvForPrincipal(
                userDetails.getMemberId(),
                action,
                targetType,
                actorName,
                summary,
                from,
                to
        );

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("domain-audit-logs-%s.csv".formatted(ProductTime.today()), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(csv);
    }
}
