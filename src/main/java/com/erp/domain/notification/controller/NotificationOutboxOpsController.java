package com.erp.domain.notification.controller;

import com.erp.domain.notification.dto.response.NotificationOutboxItemResponse;
import com.erp.domain.notification.dto.response.NotificationOutboxSummaryResponse;
import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.service.NotificationOutboxOpsService;
import com.erp.domain.notification.entity.NotificationChannel;
import com.erp.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.global.security.user.CustomUserDetails;

@RestController
@RequestMapping("/api/v1/notification-outbox")
@RequiredArgsConstructor
@Tag(name = "Notification Outbox Ops", description = "원장 전용 알림 outbox dead-letter 관측/재시도 API")
public class NotificationOutboxOpsController {

    private final NotificationOutboxOpsService notificationOutboxOpsService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "Outbox 상태 요약",
            description = "상태별 outbox 건수와 채널별 dead-letter 건수를 반환합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "원장 운영 화면 summary 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "statusCounts": { "PENDING": 3, "DELIVERED": 12, "DEAD_LETTER": 2 },
                                        "deadLetterChannelCounts": { "APP": 1, "EMAIL": 1 }
                                      }
                                    }
                                    """)
                    )
            )
    )
    public ResponseEntity<ApiResponse<NotificationOutboxSummaryResponse>> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(notificationOutboxOpsService.getSummary(kindergartenId(userDetails))));
    }

    @GetMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "Outbox timeline 조회",
            description = "전체 outbox를 최신순으로 조회하고 status, channel, q 파라미터로 운영 검색합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Outbox timeline page 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "content": [
                                          {
                                            "id": 42,
                                            "channel": "EMAIL",
                                            "status": "DEAD_LETTER",
                                            "title": "시연용 외부 알림 실패",
                                            "lastError": "SMTP connection refused"
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
    public ResponseEntity<ApiResponse<Page<NotificationOutboxItemResponse>>> getTimeline(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NotificationDeliveryStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false, name = "q") String keyword,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationOutboxOpsService.getTimeline(kindergartenId(userDetails), page, size, status, channel, keyword)
        ));
    }

    @GetMapping("/dead-letters")
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "Dead-letter 목록 조회",
            description = "최근 dead-letter 처리된 outbox를 page 단위로 조회합니다. channel 파라미터로 특정 채널만 필터링할 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Dead-letter page 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "content": [
                                          {
                                            "id": 42,
                                            "channel": "EMAIL",
                                            "status": "DEAD_LETTER",
                                            "recipientName": "김원장",
                                            "title": "시연용 외부 알림 실패",
                                            "attemptCount": 2,
                                            "lastErrorMessage": "SMTP connection refused"
                                          }
                                        ],
                                        "totalElements": 1,
                                        "totalPages": 1,
                                        "number": 0
                                      }
                                    }
                                    """)
                    )
            )
    )
    public ResponseEntity<ApiResponse<Page<NotificationOutboxItemResponse>>> getDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NotificationChannel channel,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationOutboxOpsService.getDeadLetters(kindergartenId(userDetails), page, size, channel)
        ));
    }

    @PostMapping("/{outboxId}/retry")
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "Dead-letter 수동 재시도",
            description = "dead-letter 상태의 outbox를 PENDING으로 되돌려 worker가 다시 처리하게 합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "재시도 예약 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "알림 outbox 재시도가 예약되었습니다",
                                      "data": {
                                        "id": 42,
                                        "channel": "EMAIL",
                                        "status": "PENDING",
                                        "attemptCount": 2
                                      }
                                    }
                                    """)
                    )
            )
    )
    public ResponseEntity<ApiResponse<NotificationOutboxItemResponse>> retryDeadLetter(
            @PathVariable Long outboxId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        NotificationOutboxItemResponse response = notificationOutboxOpsService
                .retryDeadLetter(kindergartenId(userDetails), outboxId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response, "알림 outbox 재시도가 예약되었습니다"));
    }

    private Long kindergartenId(CustomUserDetails userDetails) {
        return userDetails.getMember().getKindergarten().getId();
    }
}
