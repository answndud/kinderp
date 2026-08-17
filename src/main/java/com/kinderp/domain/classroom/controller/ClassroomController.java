package com.kinderp.domain.classroom.controller;

import com.kinderp.domain.classroom.dto.request.ClassroomRequest;
import com.kinderp.domain.classroom.dto.response.ClassroomResponse;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.service.ClassroomService;
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
 * 반 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    /**
     * 반 생성 (원장, 교사만 가능)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<ClassroomResponse>> create(
            @Valid @RequestBody ClassroomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long id = classroomService.createClassroom(
                request.getKindergartenId(),
                request.getName(),
                request.getAgeGroup(),
                request.capacityOrDefault(),
                userDetails.getMemberId()
        );

        Classroom classroom = classroomService.getClassroom(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(ClassroomResponse.from(classroom), "반이 생성되었습니다"));
    }

    /**
     * 반 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClassroomResponse>> getClassroom(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Classroom classroom = classroomService.getClassroom(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(ClassroomResponse.from(classroom)));
    }

    /**
     * 유치원별 반 목록 조회
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ClassroomResponse>>> getClassrooms(
            @RequestParam(required = false) Long kindergartenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (kindergartenId == null) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, "kindergartenId는 필수입니다"));
        }

        List<Classroom> classrooms = classroomService.getClassroomsByKindergarten(kindergartenId, userDetails.getMemberId());

        List<ClassroomResponse> responses = classrooms.stream()
                .map(ClassroomResponse::from)
                .toList();

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 반 수정 (원장, 교사만 가능)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<ClassroomResponse>> updateClassroom(
            @PathVariable Long id,
            @Valid @RequestBody ClassroomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        classroomService.updateClassroom(
                id,
                request.getName(),
                request.getAgeGroup(),
                request.capacityOrDefault(),
                userDetails.getMemberId()
        );

        Classroom classroom = classroomService.getClassroom(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(ClassroomResponse.from(classroom), "반이 수정되었습니다"));
    }

    /**
     * 반 삭제 (원장, 교사만 가능)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteClassroom(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        classroomService.deleteClassroom(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(null, "반이 삭제되었습니다"));
    }

    /**
     * 담임 교사 배정 (원장만 가능)
     */
    @PutMapping("/{id}/teacher")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<ClassroomResponse>> assignTeacher(
            @PathVariable Long id,
            @RequestParam Long teacherId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        classroomService.assignTeacher(id, teacherId, userDetails.getMemberId());

        Classroom classroom = classroomService.getClassroom(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(ClassroomResponse.from(classroom), "담임 교사가 배정되었습니다"));
    }

    /**
     * 담임 교사 해제 (원장만 가능)
     */
    @DeleteMapping("/{id}/teacher")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<ClassroomResponse>> removeTeacher(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        classroomService.removeTeacher(id, userDetails.getMemberId());

        Classroom classroom = classroomService.getClassroom(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(ClassroomResponse.from(classroom), "담임 교사가 해제되었습니다"));
    }
}
