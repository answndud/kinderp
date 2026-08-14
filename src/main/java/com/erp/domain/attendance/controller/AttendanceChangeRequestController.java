package com.erp.domain.attendance.controller;

import com.erp.domain.attendance.dto.request.AttendanceChangeRequestCreateRequest;
import com.erp.domain.attendance.dto.request.AttendanceChangeRequestRejectRequest;
import com.erp.domain.attendance.dto.response.AttendanceChangeRequestResponse;
import com.erp.domain.attendance.service.AttendanceChangeRequestService;
import com.erp.global.common.ApiResponse;
import com.erp.global.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance-requests")
@RequiredArgsConstructor
public class AttendanceChangeRequestController {

    private final AttendanceChangeRequestService attendanceChangeRequestService;

    @PostMapping
    @PreAuthorize("hasRole('PARENT')")
    @Operation(
            summary = "출결 변경 요청 생성",
            description = "Idempotency-Key를 보내면 같은 키와 payload의 재전송은 기존 요청 ID를 반환합니다.",
            parameters = @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    description = "재전송을 동일 요청으로 묶을 선택 키(최대 100자)",
                    required = false
            )
    )
    public ResponseEntity<ApiResponse<Long>> create(
            @Valid @RequestBody AttendanceChangeRequestCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long id = attendanceChangeRequestService.create(request, userDetails.getMemberId(), idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<AttendanceChangeRequestResponse>>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(attendanceChangeRequestService.getMyRequests(userDetails.getMemberId())));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<List<AttendanceChangeRequestResponse>>> getPendingRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                attendanceChangeRequestService.getPendingRequests(userDetails.getMemberId(), classroomId, date)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<AttendanceChangeRequestResponse>> getRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(attendanceChangeRequestService.getRequest(id, userDetails.getMemberId())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        attendanceChangeRequestService.approve(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceChangeRequestRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        attendanceChangeRequestService.reject(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        attendanceChangeRequestService.cancel(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
