package com.erp.domain.attendance.controller;

import com.erp.domain.attendance.dto.request.AttendanceRequest;
import com.erp.domain.attendance.dto.request.BulkAttendanceRequest;
import com.erp.domain.attendance.dto.request.DropOffRequest;
import com.erp.domain.attendance.dto.request.PickUpRequest;
import com.erp.domain.attendance.dto.response.AttendanceResponse;
import com.erp.domain.attendance.dto.response.AttendanceDashboardSummaryResponse;
import com.erp.domain.attendance.dto.response.BulkAttendanceResponse;
import com.erp.domain.attendance.dto.response.DailyAttendanceResponse;
import com.erp.domain.attendance.dto.response.MonthlyAttendanceReportResponse;
import com.erp.domain.attendance.dto.response.MonthlyStatisticsResponse;
import com.erp.domain.attendance.entity.Attendance;
import com.erp.domain.attendance.service.AttendanceService;
import com.erp.global.common.ApiResponse;
import com.erp.global.exception.ErrorCode;
import com.erp.global.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 출석 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "교사/원장 출석 처리와 학부모 조회 API")
public class AttendanceController {

    private static final String UPSERT_REQUEST_EXAMPLE = """
            {
              "kidId": 1,
              "attendanceDate": "2026-05-19",
              "status": "PRESENT",
              "note": "정상 등원"
            }
            """;

    private static final String UPSERT_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "message": "출석이 저장되었습니다",
              "data": {
                "id": 10,
                "kidName": "준우",
                "attendanceDate": "2026-05-19",
                "status": "PRESENT"
              }
            }
            """;

    private static final String DAILY_ATTENDANCE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": [
                {
                  "kidId": 1,
                  "kidName": "준우",
                  "status": "PRESENT",
                  "dropOffTime": "09:10",
                  "pickUpTime": "16:30"
                }
              ]
            }
            """;

    private static final String MONTHLY_REPORT_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "classroomName": "해바라기반",
                "year": 2026,
                "month": 5,
                "totalPresent": 42,
                "totalAbsent": 3
              }
            }
            """;

    private final AttendanceService attendanceService;

    @GetMapping("/dashboard-summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "요청자 범위 출결 요약",
            description = "현재 로그인한 사용자가 볼 수 있는 원생만 대상으로 기간별 출결률을 계산합니다."
    )
    public ResponseEntity<ApiResponse<AttendanceDashboardSummaryResponse>> getDashboardSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AttendanceDashboardSummaryResponse response = attendanceService.getDashboardSummary(
                startDate, endDate, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 출석 등록 (교사만 가능)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> create(
            @Valid @RequestBody AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long id = attendanceService.createAttendance(request, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendance(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "출석이 등록되었습니다"));
    }

    /**
     * 출석 등록/수정 (Upsert)
     */
    @PostMapping("/upsert")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(
            summary = "출석 등록/수정",
            description = "교사 또는 원장이 원생의 특정 날짜 출석 상태를 upsert합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            examples = @ExampleObject(value = UPSERT_REQUEST_EXAMPLE)
                    )
            ),
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "저장된 출석 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = UPSERT_RESPONSE_EXAMPLE)
                    )
            )
    )
    public ResponseEntity<ApiResponse<AttendanceResponse>> upsert(
            @Valid @RequestBody AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AttendanceResponse response = attendanceService.upsertAttendance(request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response, "출석이 저장되었습니다"));
    }

    /**
     * 반별 일괄 출석 처리
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<BulkAttendanceResponse>> bulkUpdate(
            @Valid @RequestBody BulkAttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        int updated = attendanceService.bulkUpdateAttendance(request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(new BulkAttendanceResponse(updated), "일괄 출석 처리가 완료되었습니다"));
    }

    /**
     * 출석 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getAttendance(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Attendance attendance = attendanceService.getAttendance(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance)));
    }

    /**
     * 원생별 날짜 출석 조회
     */
    @GetMapping("/kid/{kidId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getAttendanceByKidAndDate(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance)));
    }

    /**
     * 반별 일별 출석 현황
     */
    @GetMapping("/daily")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "반별 일별 출석 현황",
            description = "반 ID와 날짜 기준으로 일별 출석 상태를 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일별 출석 목록",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = DAILY_ATTENDANCE_RESPONSE_EXAMPLE)
                    )
            )
    )
    public ResponseEntity<ApiResponse<List<DailyAttendanceResponse>>> getDailyAttendance(
            @RequestParam(required = false) Long classroomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (classroomId == null) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, "반 ID는 필수입니다"));
        }

        List<DailyAttendanceResponse> responses = attendanceService.getDailyAttendanceByClassroom(classroomId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 원생별 월간 출석 목록
     */
    @GetMapping("/kid/{kidId}/monthly")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getMonthlyAttendances(
            @PathVariable Long kidId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<Attendance> attendances = attendanceService.getAttendancesByKidAndMonth(kidId, year, month, userDetails.getMemberId());

        List<AttendanceResponse> responses = attendances.stream()
                .map(attendanceService::toResponse)
                .toList();

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 반별 월간 리포트
     */
    @GetMapping("/classroom/{classroomId}/monthly-report")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(
            summary = "반별 월간 출석 리포트",
            description = "교사/원장이 반 단위 월간 출석 집계와 원생별 요약을 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "월간 출석 리포트",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = MONTHLY_REPORT_RESPONSE_EXAMPLE)
                    )
            )
    )
    public ResponseEntity<ApiResponse<MonthlyAttendanceReportResponse>> getMonthlyReport(
            @PathVariable Long classroomId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MonthlyAttendanceReportResponse response = attendanceService.getMonthlyReportByClassroom(classroomId, year, month, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 월간 출석 통계
     */
    @GetMapping("/kid/{kidId}/statistics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MonthlyStatisticsResponse>> getMonthlyStatistics(
            @PathVariable Long kidId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        MonthlyStatisticsResponse response = attendanceService.getMonthlyStatistics(kidId, year, month, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    /**
     * 출석 수정 (교사만 가능)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> updateAttendance(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.updateAttendance(id, request, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendance(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "출석 정보가 수정되었습니다"));
    }

    /**
     * 등원 기록 (교사만 가능)
     */
    @PostMapping("/kid/{kidId}/drop-off")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> recordDropOff(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DropOffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.recordDropOff(kidId, date, request, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "등원이 기록되었습니다"));
    }

    /**
     * 하원 기록 (교사만 가능)
     */
    @PostMapping("/kid/{kidId}/pick-up")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> recordPickUp(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody PickUpRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.recordPickUp(kidId, date, request, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "하원이 기록되었습니다"));
    }

    /**
     * 결석 처리 (교사만 가능)
     */
    @PostMapping("/kid/{kidId}/absent")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markAbsent(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.markAbsent(kidId, date, note, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "결석 처리되었습니다"));
    }

    /**
     * 지각 처리 (교사만 가능)
     */
    @PostMapping("/kid/{kidId}/late")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markLate(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) java.time.LocalTime dropOffTime,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.markLate(kidId, date, dropOffTime, note, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "지각 처리되었습니다"));
    }

    /**
     * 조퇴 처리 (교사만 가능)
     */
    @PostMapping("/kid/{kidId}/early-leave")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markEarlyLeave(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) java.time.LocalTime pickUpTime,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.markEarlyLeave(kidId, date, pickUpTime, note, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "조퇴 처리되었습니다"));
    }

    /**
     * 병결 처리 (교사만 가능)
     */
    @PostMapping("/kid/{kidId}/sick-leave")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markSickLeave(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        attendanceService.markSickLeave(kidId, date, note, userDetails.getMemberId());

        Attendance attendance = attendanceService.getAttendanceByKidAndDate(kidId, date, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(attendanceService.toResponse(attendance), "병결 처리되었습니다"));
    }

    /**
     * 출석 삭제 (교사만 가능)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteAttendance(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        attendanceService.deleteAttendance(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(null, "출석 정보가 삭제되었습니다"));
    }
}
