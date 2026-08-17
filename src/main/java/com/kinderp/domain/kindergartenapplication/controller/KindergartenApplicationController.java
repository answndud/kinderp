package com.kinderp.domain.kindergartenapplication.controller;

import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.security.user.CustomUserDetails;
import com.kinderp.domain.kindergartenapplication.dto.request.KindergartenApplicationRequest;
import com.kinderp.domain.kindergartenapplication.dto.request.RejectRequest;
import com.kinderp.domain.kindergartenapplication.dto.response.KindergartenApplicationResponse;
import com.kinderp.domain.kindergartenapplication.service.KindergartenApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kindergarten-applications")
@RequiredArgsConstructor
public class KindergartenApplicationController {

    private final KindergartenApplicationService applicationService;

    /**
     * 교사 지원 신청
     */
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Long>> apply(
            @Valid @RequestBody KindergartenApplicationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long applicationId = applicationService.apply(userDetails.getMemberId(), request);
        return ResponseEntity.ok(ApiResponse.success(applicationId));
    }

    /**
     * 내 지원서 목록
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<KindergartenApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KindergartenApplicationResponse> applications = applicationService.getMyApplications(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    /**
     * 유치원별 대기 지원서 (원장용)
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<KindergartenApplicationResponse>>> getPendingApplications(
            @RequestParam Long kindergartenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KindergartenApplicationResponse> applications = applicationService.getPendingApplications(kindergartenId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    /**
     * 지원서 상세 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<KindergartenApplicationResponse>> getApplication(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        KindergartenApplicationResponse application = applicationService.getApplication(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(application));
    }

    /**
     * 지원서 승인
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.approve(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 지원서 거절
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.reject(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 지원서 취소
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.cancel(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
