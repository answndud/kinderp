package com.kinderp.domain.kindergarten.controller;

import com.kinderp.domain.kindergarten.dto.request.KindergartenRequest;
import com.kinderp.domain.kindergarten.dto.response.KindergartenResponse;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.service.KindergartenService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 유치원 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/kindergartens")
@RequiredArgsConstructor
public class KindergartenController {

    private final KindergartenService kindergartenService;

    /**
     * 유치원 등록 (원장만 가능)
     */
    @PostMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<KindergartenResponse>> create(
            @Valid @RequestBody KindergartenRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long id = kindergartenService.register(
                request.getName(),
                request.getAddress(),
                request.getPhone(),
                request.getOpenTime(),
                request.getCloseTime(),
                userDetails.getMemberId()
        );

        Kindergarten kindergarten = kindergartenService.getKindergartenForRequester(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(KindergartenResponse.from(kindergarten), "유치원이 등록되었습니다"));
    }

    /**
     * 유치원 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KindergartenResponse>> getKindergarten(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Kindergarten kindergarten = kindergartenService.getKindergartenForRequester(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(KindergartenResponse.from(kindergarten)));
    }

    /**
     * 전체 유치원 목록 조회
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<KindergartenResponse>>> getAllKindergartens(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Kindergarten> kindergartens = kindergartenService.getKindergartensForRequester(userDetails.getMemberId());

        List<KindergartenResponse> responses = kindergartens.stream()
                .map(KindergartenResponse::from)
                .toList();

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 유치원 수정 (원장만 가능)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<KindergartenResponse>> updateKindergarten(
            @PathVariable Long id,
            @Valid @RequestBody KindergartenRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        kindergartenService.updateKindergartenForRequester(
                id,
                userDetails.getMemberId(),
                request.getName(),
                request.getAddress(),
                request.getPhone(),
                request.getOpenTime(),
                request.getCloseTime()
        );

        Kindergarten kindergarten = kindergartenService.getKindergartenForRequester(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(KindergartenResponse.from(kindergarten), "유치원 정보가 수정되었습니다"));
    }

    /**
     * 유치원 삭제 (원장만 가능)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<Void>> deleteKindergarten(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        kindergartenService.deleteKindergartenForRequester(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(null, "유치원이 삭제되었습니다"));
    }
}
