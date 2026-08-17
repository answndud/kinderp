package com.kinderp.domain.announcement.controller;

import com.kinderp.domain.announcement.dto.request.AnnouncementRequest;
import com.kinderp.domain.announcement.entity.Announcement;
import com.kinderp.domain.announcement.service.AnnouncementService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 공지사항 뷰 컨트롤러
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AnnouncementViewController {

    private final AnnouncementService announcementService;
    private final MemberService memberService;

    /**
     * 공지사항 목록 페이지
     */
    @GetMapping("/announcements")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String announcementsPage(@AuthenticationPrincipal Object userDetails) {
        return "announcement/announcements";
    }

    /**
     * 공지사항 목록 조각 (HTMX)
     */
    @GetMapping("/announcements/list")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String announcementList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "false") boolean importantOnly,
            Model model) {

        // 회원을 유치원 포함하여 조회 (LazyInitializationException 방지)
        Member member = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());
        Long kindergartenId = member.getKindergarten().getId();

        var announcements = announcementService.getAnnouncementsByKindergartenForView(kindergartenId, userDetails.getMemberId());

        // 중요 공지만 필터링
        if (importantOnly) {
            announcements = announcements.stream()
                    .filter(com.kinderp.domain.announcement.entity.Announcement::isImportant)
                    .toList();
        }

        // Entity를 DTO로 변환
        var announcementResponses = announcements.stream()
                .map(announcementService::toResponse)
                .toList();

        model.addAttribute("announcements", announcementResponses);
        model.addAttribute("importantOnly", importantOnly);
        return "announcement/fragments/list :: announcementList";
    }

    /**
     * 공지사항 작성 페이지
     */
    @GetMapping("/announcement/write")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String writeForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        
        // LazyInitializationException 방지를 위해 유치원 포함하여 조회
        Member member = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());
        model.addAttribute("kindergartenId", member.getKindergarten().getId());
        
        return "announcement/write";
    }

    /**
     * 공지사항 상세 페이지
     */
    @GetMapping("/announcement/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String detail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Announcement announcement = announcementService.getAnnouncement(id, userDetails.getMemberId());
        model.addAttribute("announcement", announcementService.toResponse(announcement));
        return "announcement/detail";
    }

    /**
     * 공지사항 수정 페이지
     */
    @GetMapping("/announcement/{id}/edit")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Announcement announcement = announcementService.getAnnouncementWithoutIncrement(id, userDetails.getMemberId());
        model.addAttribute("announcement", announcementService.toResponse(announcement));
        return "announcement/edit";
    }

    /**
     * 공지사항 작성
     */
    @PostMapping("/announcement")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String write(
            @ModelAttribute AnnouncementRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        log.debug("공지사항 작성 요청 - kindergartenId: {}, title: {}, content: {}, isImportant: {}", 
                  request.getKindergartenId(), request.getTitle(), 
                  request.getContent() != null ? request.getContent().substring(0, Math.min(20, request.getContent().length())) : "null", 
                  request.getIsImportant());

        // 수동 검증
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            log.warn("공지사항 작성 실패 - 제목 없음");
            redirectAttributes.addFlashAttribute("error", "제목을 입력해주세요.");
            return "redirect:/announcement/write";
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            log.warn("공지사항 작성 실패 - 내용 없음");
            redirectAttributes.addFlashAttribute("error", "내용을 입력해주세요.");
            return "redirect:/announcement/write";
        }
        if (request.getKindergartenId() == null) {
            log.warn("공지사항 작성 실패 - 유치원 정보 없음");
            redirectAttributes.addFlashAttribute("error", "유치원 정보가 없습니다.");
            return "redirect:/announcement/write";
        }

        try {
            Long announcementId = announcementService.createAnnouncement(request, userDetails.getMemberId());
            log.info("공지사항 작성 성공 - id: {}", announcementId);
            redirectAttributes.addFlashAttribute("message", "공지사항이 작성되었습니다.");
            return "redirect:/announcement/" + announcementId;
        } catch (Exception e) {
            log.error("공지사항 작성 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "공지사항 작성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/announcement/write";
        }
    }

    /**
     * 공지사항 수정
     */
    @PostMapping("/announcement/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String update(
            @PathVariable Long id,
            @ModelAttribute AnnouncementRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        log.debug("공지사항 수정 요청 - id: {}, kindergartenId: {}, title: {}, content: {}, isImportant: {}", 
                  id, request.getKindergartenId(), request.getTitle(), 
                  request.getContent() != null ? request.getContent().substring(0, Math.min(20, request.getContent().length())) : "null", 
                  request.getIsImportant());

        // 수동 검증
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            log.warn("공지사항 수정 실패 - 제목 없음");
            redirectAttributes.addFlashAttribute("error", "제목을 입력해주세요.");
            return "redirect:/announcement/" + id + "/edit";
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            log.warn("공지사항 수정 실패 - 내용 없음");
            redirectAttributes.addFlashAttribute("error", "내용을 입력해주세요.");
            return "redirect:/announcement/" + id + "/edit";
        }

        try {
            announcementService.updateAnnouncement(id, request, userDetails.getMemberId());
            log.info("공지사항 수정 성공 - id: {}", id);
            redirectAttributes.addFlashAttribute("message", "공지사항이 수정되었습니다.");
            return "redirect:/announcement/" + id;
        } catch (Exception e) {
            log.error("공지사항 수정 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "공지사항 수정에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/announcement/" + id + "/edit";
        }
    }

    /**
     * 공지사항 삭제
     */
    @PostMapping("/announcement/{id}/delete")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            announcementService.deleteAnnouncement(id, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "공지사항이 삭제되었습니다.");
            return "redirect:/announcements";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "공지사항 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/announcement/" + id;
        }
    }

    /**
     * 중요 공지 토글
     */
    @PostMapping("/announcement/{id}/toggle-important")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String toggleImportant(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            announcementService.toggleImportant(id, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "중요 공지 설정이 변경되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "중요 공지 설정 변경에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/announcement/" + id;
    }
}
