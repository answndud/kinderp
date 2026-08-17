package com.kinderp.domain.notepad.controller;

import com.kinderp.domain.notepad.dto.request.NotepadRequest;
import com.kinderp.domain.notepad.dto.response.NotepadDetailResponse;
import com.kinderp.domain.notepad.dto.response.NotepadResponse;
import com.kinderp.domain.notepad.entity.Notepad;
import com.kinderp.domain.notepad.service.NotepadService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.common.PageRequests;
import com.kinderp.global.security.user.CustomUserDetails;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 알림장 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/notepads")
public class NotepadController {

    private final NotepadService notepadService;

    public NotepadController(NotepadService notepadService) {
        this.notepadService = notepadService;
    }

    /**
     * 알림장 목록 조회 (유치원별)
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotepadResponse>>> getNotepads(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Long kidId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequests.of(page, size, 20, Sort.by("createdAt").descending());
        Page<NotepadResponse> responses;

        if (kidId != null) {
            // 원생별 조회
            responses = notepadService.getKidNotepads(kidId, pageable, userDetails.getMemberId());
        } else if (classroomId != null) {
            // 반별 조회
            responses = notepadService.getClassroomNotepads(classroomId, pageable, userDetails.getMemberId());
        } else {
            if (userDetails.getRole() == com.kinderp.domain.member.entity.MemberRole.PARENT) {
                responses = notepadService.getNotepadsForParent(userDetails.getMemberId(), pageable);
            } else {
                Long kindergartenId = userDetails.getMember().getKindergarten().getId();
                responses = notepadService.getNotepadsByKindergarten(kindergartenId, pageable, userDetails.getMemberId());
            }
        }

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 알림장 생성 (교사, 원장만 가능)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<NotepadResponse>> create(
            @Valid @RequestBody NotepadRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long writerId = userDetails.getMemberId();

        Long id = notepadService.createNotepad(request, writerId);

        Notepad notepad = notepadService.getNotepad(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(notepadService.toResponse(notepad), "알림장이 작성되었습니다"));
    }

    /**
     * 알림장 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotepadDetailResponse>> getNotepad(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long readerId = userDetails.getMemberId();

        NotepadDetailResponse response = notepadService.getNotepadDetail(id, readerId);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    /**
     * 반별 알림장 목록 (페이지)
     */
    @GetMapping("/classroom/{classroomId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotepadResponse>>> getClassroomNotepads(
            @PathVariable Long classroomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Pageable pageable = PageRequests.of(page, size, 10, Sort.by("createdAt").descending());
        Page<NotepadResponse> responses = notepadService.getClassroomNotepads(classroomId, pageable, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 원생별 알림장 목록 (페이지)
     */
    @GetMapping("/kid/{kidId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotepadResponse>>> getKidNotepads(
            @PathVariable Long kidId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Pageable pageable = PageRequests.of(page, size, 10, Sort.by("createdAt").descending());
        Page<NotepadResponse> responses = notepadService.getKidNotepads(kidId, pageable, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 학부모용 알림장 목록 (반 전체 + 내 원생)
     */
    @GetMapping("/parent")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<Page<NotepadResponse>>> getNotepadsForParent(
            @RequestParam Long classroomId,
            @RequestParam Long kidId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Pageable pageable = PageRequests.of(page, size, 10, Sort.by("createdAt").descending());
        Page<NotepadResponse> responses = notepadService.getNotepadsForParent(classroomId, kidId, pageable, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    /**
     * 알림장 수정 (작성자만 가능)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<NotepadResponse>> updateNotepad(
            @PathVariable Long id,
            @Valid @RequestBody NotepadRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long writerId = userDetails.getMemberId();

        notepadService.updateNotepad(id, request, writerId);

        Notepad notepad = notepadService.getNotepad(id, userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(notepadService.toResponse(notepad), "알림장이 수정되었습니다"));
    }

    /**
     * 알림장 삭제 (작성자 또는 원장만 가능)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteNotepad(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long requesterId = userDetails.getMemberId();

        notepadService.deleteNotepad(id, requesterId);

        return ResponseEntity
                .ok(ApiResponse.success(null, "알림장이 삭제되었습니다"));
    }

    /**
     * 알림장 읽음 처리 (학부모만 가능)
     */
    @PostMapping("/{id}/read")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long readerId = userDetails.getMemberId();

        notepadService.markAsRead(id, readerId);

        return ResponseEntity
                .ok(ApiResponse.success(null, "읽음 처리되었습니다"));
    }
}
