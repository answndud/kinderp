package com.erp.domain.authaudit.controller;

import com.erp.domain.authaudit.dto.response.AuthAuditLogResponse;
import com.erp.domain.authaudit.entity.AuthAuditEventType;
import com.erp.domain.authaudit.entity.AuthAuditResult;
import com.erp.domain.authaudit.service.AuthAuditLogQueryService;
import com.erp.domain.member.entity.MemberAuthProvider;
import com.erp.global.common.ApiResponse;
import com.erp.global.common.ProductTime;
import com.erp.global.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/auth/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Auth Audit", description = "원장 전용 인증 이벤트 조회/CSV export API")
public class AuthAuditLogController {

    private final AuthAuditLogQueryService authAuditLogQueryService;

    @GetMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "인증 감사 로그 조회",
            description = "로그인, refresh, 소셜 연결/해제 이벤트를 같은 유치원 원장 기준으로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "인증 감사 로그 page 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "content": [
                                          {
                                            "memberEmail": "principal@test.com",
                                            "eventType": "LOGIN",
                                            "result": "SUCCESS",
                                            "provider": "LOCAL",
                                            "clientIp": "198.51.100.10"
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
    public ResponseEntity<ApiResponse<Page<AuthAuditLogResponse>>> getAuditLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuthAuditEventType eventType,
            @RequestParam(required = false) AuthAuditResult result,
            @RequestParam(required = false) MemberAuthProvider provider,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AuthAuditLogResponse> responses = authAuditLogQueryService.getAuditLogsForPrincipal(
                userDetails.getMemberId(),
                eventType,
                result,
                provider,
                email,
                reason,
                from,
                to,
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(summary = "인증 감사 로그 CSV export", description = "현재 필터 조건에 맞는 인증 감사 로그를 CSV 파일로 내려받습니다.")
    public ResponseEntity<byte[]> exportAuditLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuthAuditEventType eventType,
            @RequestParam(required = false) AuthAuditResult result,
            @RequestParam(required = false) MemberAuthProvider provider,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        byte[] csv = authAuditLogQueryService.exportAuditLogsCsvForPrincipal(
                userDetails.getMemberId(),
                eventType,
                result,
                provider,
                email,
                reason,
                from,
                to
        );

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("auth-audit-logs-%s.csv".formatted(ProductTime.today()), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(csv);
    }
}
