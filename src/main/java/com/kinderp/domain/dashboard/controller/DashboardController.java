package com.kinderp.domain.dashboard.controller;

import com.kinderp.domain.dashboard.dto.response.DashboardStatisticsResponse;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "원장용 운영 지표 API")
public class DashboardController {

    private static final String STATISTICS_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "totalKids": 12,
                "todayPresent": 8,
                "todayAbsent": 1,
                "totalTeachers": 2,
                "unreadNotifications": 3
              }
            }
            """;

    private final DashboardService dashboardService;

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('PRINCIPAL')")
    @Operation(
            summary = "원장 대시보드 통계",
            description = "원장 소속 유치원의 출석, 회원, 공지, 알림장 등 운영 지표를 캐시 기반으로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대시보드 통계 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = STATISTICS_RESPONSE_EXAMPLE)
                    )
            )
    )
    public ResponseEntity<ApiResponse<DashboardStatisticsResponse>> getStatistics(
            @AuthenticationPrincipal com.kinderp.global.security.user.CustomUserDetails userDetails) {
        Kindergarten kindergarten = userDetails.getMember().getKindergarten();
        DashboardStatisticsResponse response = dashboardService.getDashboardStatistics(kindergarten);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
