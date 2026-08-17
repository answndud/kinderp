package com.kinderp.domain.application.controller;

import com.kinderp.domain.kidapplication.dto.response.KidApplicationResponse;
import com.kinderp.domain.kidapplication.service.KidApplicationService;
import com.kinderp.domain.kindergartenapplication.dto.response.KindergartenApplicationResponse;
import com.kinderp.domain.kindergartenapplication.service.KindergartenApplicationService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ApplicationViewController {

    private final MemberService memberService;
    private final KindergartenApplicationService kindergartenApplicationService;
    private final KidApplicationService kidApplicationService;

    @GetMapping("/applications/pending")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String pendingPage() {
        return "applications/pending";
    }

    @GetMapping("/applications/pending/content")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String pendingContent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Member member = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());

        model.addAttribute("role", member.getRole());
        model.addAttribute("status", member.getStatus());
        model.addAttribute("kindergartenId", member.getKindergarten() != null ? member.getKindergarten().getId() : null);
        model.addAttribute("kindergartenName", member.getKindergarten() != null ? member.getKindergarten().getName() : null);

        if (member.getRole() == MemberRole.PRINCIPAL) {
            if (member.getKindergarten() == null) {
                model.addAttribute("teacherPending", List.of());
                model.addAttribute("kidPending", List.of());
            } else {
                Long kindergartenId = requireKindergartenId(member);

                List<KindergartenApplicationResponse> teacherPending =
                        kindergartenApplicationService.getPendingApplications(kindergartenId, member.getId());
                List<KidApplicationResponse> kidPending =
                        kidApplicationService.getPendingApplications(kindergartenId, member.getId());

                model.addAttribute("teacherPending", teacherPending);
                model.addAttribute("kidPending", kidPending);
            }
        }

        if (member.getRole() == MemberRole.TEACHER) {
            // 기존 데이터/관리자 생성 등으로 ACTIVE인데 유치원 미배정인 케이스도 있으므로,
            // 유치원 미배정 상태에서는 신청 폼/내 지원 목록을 보여준다.
            if (member.getStatus() == MemberStatus.PENDING || member.getKindergarten() == null) {
                List<KindergartenApplicationResponse> myTeacherApplications =
                        kindergartenApplicationService.getMyApplications(member.getId());
                model.addAttribute("myTeacherApplications", myTeacherApplications);
            } else {
                Long kindergartenId = member.getKindergarten().getId();
                List<KidApplicationResponse> kidPending =
                        kidApplicationService.getPendingApplications(kindergartenId, member.getId());
                model.addAttribute("kidPending", kidPending);
            }
        }

        if (member.getRole() == MemberRole.PARENT) {
            List<KidApplicationResponse> myKidApplications =
                    kidApplicationService.getMyApplications(member.getId());
            model.addAttribute("myKidApplications", myKidApplications);
        }

        return "applications/fragments/pending-content :: content";
    }

    private Long requireKindergartenId(Member member) {
        if (member.getKindergarten() == null) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND);
        }
        return member.getKindergarten().getId();
    }
}
