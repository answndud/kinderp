package com.kinderp.domain.notepad.controller;

import com.kinderp.domain.classroom.service.ClassroomService;
import com.kinderp.domain.kid.service.KidService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.domain.notepad.dto.request.NotepadRequest;
import com.kinderp.domain.notepad.dto.response.NotepadDetailResponse;
import com.kinderp.domain.notepad.dto.response.NotepadResponse;
import com.kinderp.domain.notepad.service.NotepadService;
import com.kinderp.global.common.PageRequests;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.user.CustomUserDetails;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import lombok.extern.slf4j.Slf4j;


/**
 * 알림장 뷰 컨트롤러
 */
@Controller
@Slf4j
public class NotepadViewController {

    private final NotepadService notepadService;
    private final ClassroomService classroomService;
    private final KidService kidService;
    private final MemberService memberService;

    public NotepadViewController(
            NotepadService notepadService,
            ClassroomService classroomService,
            KidService kidService,
            MemberService memberService) {
        this.notepadService = notepadService;
        this.classroomService = classroomService;
        this.kidService = kidService;
        this.memberService = memberService;
    }


    /**
     * 알림장 목록 페이지
     */
    @GetMapping("/notepad")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String notepadPage(@AuthenticationPrincipal Object userDetails) {
        return "notepad/notepad";
    }

    /**
     * 알림장 목록 조각 (HTMX)
     */
    @GetMapping("/notepad/list")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String notepadList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Long kidId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequests.of(page, size, 20, Sort.by("createdAt").descending());
        Page<NotepadResponse> notepads;

        if (userDetails.getRole() == MemberRole.PARENT) {
            if (kidId != null) {
                var myKids = kidService.getKidsByParent(userDetails.getMemberId());
                var selectedKid = myKids.stream()
                        .filter(k -> k.getId().equals(kidId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));

                Long kidClassroomId = selectedKid.getClassroom().getId();
                notepads = notepadService.getNotepadsForParent(kidClassroomId, kidId, pageable, userDetails.getMemberId());
            } else {
                notepads = notepadService.getNotepadsForParent(userDetails.getMemberId(), pageable);
            }
        } else {
            if (kidId != null) {
                notepads = notepadService.getKidNotepads(kidId, pageable, userDetails.getMemberId());
            } else if (classroomId != null) {
                notepads = notepadService.getClassroomNotepads(classroomId, pageable, userDetails.getMemberId());
            } else {
                Member member = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());
                Long kindergartenId = member.getKindergarten().getId();
                notepads = notepadService.getNotepadsByKindergarten(kindergartenId, pageable, userDetails.getMemberId());
            }
        }

        model.addAttribute("notepads", notepads);
        return "notepad/fragments/list :: notepadList";
    }
 
    /**
     * 알림장 작성 페이지
     */
    @GetMapping("/notepad/write")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String writeForm() {
        return "notepad/write";
    }

    /**
     * 알림장 상세 페이지
     */
    @GetMapping("/notepad/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String detail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        NotepadDetailResponse notepad = notepadService.getNotepadDetail(id, userDetails.getMemberId());

        // 읽음 표시
        try {
            notepadService.markAsRead(id, userDetails.getMemberId());
        } catch (Exception ignored) {
            // 이미 읽었거나 권한 없는 경우 무시
        }

        model.addAttribute("notepad", notepad);
        return "notepad/detail";
    }

    /**
     * 알림장 수정 페이지
     */
    @GetMapping("/notepad/{id}/edit")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        NotepadDetailResponse notepad = notepadService.getNotepadDetail(id, userDetails.getMemberId());
        model.addAttribute("notepad", notepad);
        return "notepad/edit";
    }

    /**
     * 알림장 작성
     */
    @PostMapping("/notepad")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String write(
            @ModelAttribute NotepadRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            Long notepadId = notepadService.createNotepad(request, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "알림장이 작성되었습니다.");
            return "redirect:/notepad/" + notepadId;
        } catch (Exception e) {
            log.error("알림장 작성 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "알림장 작성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/notepad/write";
        }
    }

    /**
     * 알림장 수정
     */
    @PostMapping("/notepad/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String update(
            @PathVariable Long id,
            @ModelAttribute NotepadRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            notepadService.updateNotepad(id, request, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "알림장이 수정되었습니다.");
            return "redirect:/notepad/" + id;
        } catch (Exception e) {
            log.error("알림장 수정 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "알림장 수정에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/notepad/" + id + "/edit";
        }
    }

    /**
     * 알림장 삭제
     */
    @PostMapping("/notepad/{id}/delete")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            notepadService.deleteNotepad(id, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "알림장이 삭제되었습니다.");
            return "redirect:/notepad";
        } catch (Exception e) {
            log.error("알림장 삭제 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "알림장 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/notepad/" + id;
        }
    }
}
