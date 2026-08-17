package com.kinderp.domain.kid.controller;

import com.kinderp.domain.kid.dto.request.AssignParentRequest;
import com.kinderp.domain.kid.dto.request.KidRequest;
import com.kinderp.domain.kid.dto.request.UpdateClassroomRequest;
import com.kinderp.domain.kid.dto.response.ClassroomKidCountResponse;
import com.kinderp.domain.kid.dto.response.KidDetailResponse;
import com.kinderp.domain.kid.dto.response.KidResponse;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.service.KidService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.kinderp.global.security.user.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 원생 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/kids")
@RequiredArgsConstructor
public class KidController {

    private final KidService kidService;

    /**
     * 원생 생성 (원장, 교사만 가능)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<KidResponse>> create(
            @Valid @RequestBody KidRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long id = kidService.createKid(request, userDetails.getMemberId());

        Kid kid = kidService.getKid(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(KidResponse.from(kid), "원생이 등록되었습니다"));
    }

    /**
     * 원생 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KidDetailResponse>> getKid(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        KidDetailResponse response = kidService.getKidDetail(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    /**
     * 반별 원생 목록 조회
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<KidResponse>>> getKids(
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Long kindergartenId,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (classroomId == null && kindergartenId == null) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, "classroomId 또는 kindergartenId 중 하나는 필수입니다"));
        }

        List<Kid> kids;

        if (classroomId != null) {
            if (name != null && !name.isBlank()) {
                kids = kidService.searchKidsByName(classroomId, name, userDetails.getMemberId());
            } else {
                kids = kidService.getKidsByClassroom(classroomId, userDetails.getMemberId());
            }
        } else if (kindergartenId != null) {
            if (name != null && !name.isBlank()) {
                kids = kidService.searchKidsByKindergarten(kindergartenId, name, userDetails.getMemberId());
            } else {
                kids = kidService.getKidsByKindergarten(kindergartenId, userDetails.getMemberId());
            }
        } else {
            kids = List.of();
        }

        List<KidResponse> responses = kids.stream()
                .map(KidResponse::from)
                .toList();

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 반별 원생 수 조회
     */
    @GetMapping("/classroom-counts")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<List<ClassroomKidCountResponse>>> getClassroomCounts(
            @RequestParam Long kindergartenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        java.util.Map<Long, Long> counts = kidService.getClassroomCounts(kindergartenId, userDetails.getMemberId());
        List<ClassroomKidCountResponse> responses = counts.entrySet().stream()
                .map(entry -> ClassroomKidCountResponse.of(entry.getKey(), entry.getValue()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 원생 목록 조회 (페이지)
     */
    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<KidResponse>>> getKidsPage(
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Long kindergartenId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(defaultValue = "name") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                safePage,
                safeSize,
                resolveSort(sort)
        );

        org.springframework.data.domain.Page<Kid> kidsPage;

        if (classroomId != null) {
            if (name != null && !name.isBlank()) {
                kidsPage = kidService.searchKidsByName(classroomId, name, pageable, userDetails.getMemberId());
            } else {
                kidsPage = kidService.getKidsByClassroom(classroomId, pageable, userDetails.getMemberId());
            }
        } else if (kindergartenId != null) {
            if (name != null && !name.isBlank()) {
                kidsPage = kidService.searchKidsByKindergarten(kindergartenId, name, pageable, userDetails.getMemberId());
            } else {
                kidsPage = kidService.getKidsByKindergarten(kindergartenId, pageable, userDetails.getMemberId());
            }
        } else {
            kidsPage = org.springframework.data.domain.Page.empty(pageable);
        }

        org.springframework.data.domain.Page<KidResponse> responses = kidsPage.map(KidResponse::from);

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    private org.springframework.data.domain.Sort resolveSort(String sortKey) {
        if ("recent".equalsIgnoreCase(sortKey)) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        }
        if ("age".equalsIgnoreCase(sortKey)) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "birthDate");
        }
        return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name");
    }

    /**
     * 학부모의 원생 목록 조회
     */
    @GetMapping("/my-kids")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<KidResponse>>> getMyKids(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long parentId = userDetails.getMemberId();

        List<Kid> kids = kidService.getKidsByParent(parentId);

        List<KidResponse> responses = kids.stream()
                .map(KidResponse::from)
                .toList();

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }


    /**
     * 원생 수정 (원장, 교사만 가능)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<KidResponse>> updateKid(
            @PathVariable Long id,
            @Valid @RequestBody KidRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        kidService.updateKid(id, request.getName(), request.getBirthDate(), request.getGender(), userDetails.getMemberId());

        Kid kid = kidService.getKid(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(KidResponse.from(kid), "원생 정보가 수정되었습니다"));
    }

    /**
     * 반 배정 변경 (원장만 가능)
     */
    @PutMapping("/{id}/classroom")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<KidResponse>> updateClassroom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassroomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        kidService.updateClassroom(id, request, userDetails.getMemberId());

        Kid kid = kidService.getKid(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(KidResponse.from(kid), "반이 변경되었습니다"));
    }

    /**
     * 학부모 연결 (원장만 가능)
     */
    @PostMapping("/{id}/parents")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<KidDetailResponse>> assignParent(
            @PathVariable Long id,
            @Valid @RequestBody AssignParentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        kidService.assignParent(id, request, userDetails.getMemberId());

        KidDetailResponse response = kidService.getKidDetail(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(response, "학부모가 연결되었습니다"));
    }

    /**
     * 학부모 연결 해제 (원장만 가능)
     */
    @DeleteMapping("/{id}/parents/{parentId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<KidDetailResponse>> removeParent(
            @PathVariable Long id,
            @PathVariable Long parentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        kidService.removeParent(id, parentId, userDetails.getMemberId());

        KidDetailResponse response = kidService.getKidDetail(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(response, "학부모 연결이 해제되었습니다"));
    }

    /**
     * 원생 삭제 (원장만 가능)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<Void>> deleteKid(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        kidService.deleteKid(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(null, "원생이 삭제되었습니다"));
    }
}
