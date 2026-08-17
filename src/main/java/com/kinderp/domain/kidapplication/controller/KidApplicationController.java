package com.kinderp.domain.kidapplication.controller;

import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.security.user.CustomUserDetails;
import com.kinderp.domain.kidapplication.dto.request.AcceptKidApplicationOfferRequest;
import com.kinderp.domain.kidapplication.dto.request.ApproveKidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.KidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.OfferKidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.RejectRequest;
import com.kinderp.domain.kidapplication.dto.request.WaitlistKidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.response.KidApplicationResponse;
import com.kinderp.domain.kidapplication.service.KidApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kid-applications")
@RequiredArgsConstructor
@Tag(name = "Kid Application Workflow", description = "원생 입학 신청, 대기열, offer, 수락/승인 workflow API")
public class KidApplicationController {

    private final KidApplicationService applicationService;

    /**
     * 원생 입학 신청
     */
    @PostMapping
    @PreAuthorize("hasRole('PARENT')")
    @Operation(
            summary = "원생 입학 신청",
            description = "학부모가 특정 유치원에 원생 입학 신청을 생성합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "kindergartenId": 1,
                                      "kidName": "민준",
                                      "birthDate": "2022-05-10",
                                      "gender": "MALE",
                                      "preferredClassroomId": 1,
                                      "notes": "신규 입학 상담 후 서류 대기"
                                    }
                                    """)
                    )
            ),
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "생성된 신청 ID",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": 1001
                                    }
                                    """)
                    )
            )
    )
    public ResponseEntity<ApiResponse<Long>> apply(
            @Valid @RequestBody KidApplicationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long applicationId = applicationService.apply(request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(applicationId));
    }

    /**
     * 내 자녀 입학 신청 목록
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "내 입학 신청 목록", description = "학부모가 본인이 제출한 원생 입학 신청 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<KidApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KidApplicationResponse> applications = applicationService.getMyApplications(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    /**
     * 유치원별 대기 입학 신청 (교사/원장용)
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(summary = "승인 대기 신청 목록", description = "원장/교사가 같은 유치원의 PENDING 입학 신청을 조회합니다.")
    public ResponseEntity<ApiResponse<List<KidApplicationResponse>>> getPendingApplications(
            @RequestParam Long kindergartenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KidApplicationResponse> applications = applicationService.getPendingApplications(kindergartenId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(
            summary = "검토 큐 조회",
            description = "PENDING, WAITLISTED, OFFERED 상태를 함께 조회해 운영 검토 큐를 구성합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "운영 검토 큐 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1001,
                                          "kidName": "민준",
                                          "status": "PENDING",
                                          "kindergartenName": "해바라기 유치원",
                                          "preferredClassroomName": "해바라기반"
                                        },
                                        {
                                          "id": 1002,
                                          "kidName": "유나",
                                          "status": "WAITLISTED",
                                          "assignedClassroomName": "해바라기반"
                                        }
                                      ]
                                    }
                                    """)
                    )
            )
    )
    public ResponseEntity<ApiResponse<List<KidApplicationResponse>>> getReviewQueueApplications(
            @RequestParam Long kindergartenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KidApplicationResponse> applications = applicationService.getReviewQueueApplications(kindergartenId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    /**
     * 입학 신청 상세 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    @Operation(summary = "입학 신청 상세 조회", description = "신청자 학부모 또는 같은 유치원의 원장/교사가 입학 신청 상세를 조회합니다.")
    public ResponseEntity<ApiResponse<KidApplicationResponse>> getApplication(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        KidApplicationResponse application = applicationService.getApplication(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(application));
    }

    /**
     * 입학 신청 승인
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(summary = "입학 즉시 승인", description = "정원 검증 후 원생과 부모 연결을 생성하고 신청을 APPROVED로 전환합니다.")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveKidApplicationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.approve(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/waitlist")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(summary = "입학 대기열 등록", description = "정원 부족 등으로 신청을 WAITLISTED 상태로 전환합니다.")
    public ResponseEntity<ApiResponse<Void>> waitlist(
            @PathVariable Long id,
            @Valid @RequestBody WaitlistKidApplicationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.placeOnWaitlist(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/offer")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(summary = "입학 offer 발송", description = "좌석을 확인한 뒤 학부모가 수락할 수 있는 OFFERED 상태로 전환합니다.")
    public ResponseEntity<ApiResponse<Void>> offer(
            @PathVariable Long id,
            @Valid @RequestBody OfferKidApplicationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.offer(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/accept-offer")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "입학 offer 수락", description = "학부모가 offer를 수락하면 원생과 부모 연결을 생성하고 APPROVED로 전환합니다.")
    public ResponseEntity<ApiResponse<Void>> acceptOffer(
            @PathVariable Long id,
            @Valid @RequestBody AcceptKidApplicationOfferRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.acceptOffer(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 입학 신청 거절
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    @Operation(summary = "입학 신청 거절", description = "검토 가능한 신청을 REJECTED 상태로 전환하고 거절 사유를 남깁니다.")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.reject(id, request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 입학 신청 취소
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "입학 신청 취소", description = "학부모가 본인의 활성 입학 신청을 CANCELLED 상태로 전환합니다.")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.cancel(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
