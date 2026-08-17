package com.kinderp.domain.attendance.controller;

import com.kinderp.domain.attendance.entity.AttendanceStatus;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AttendanceChangeRequestViewController {

    @GetMapping("/attendance-requests")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String attendanceRequestPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        MemberRole role = userDetails.getRole();
        model.addAttribute("attendanceStatuses", AttendanceStatus.values());
        model.addAttribute("isParent", role == MemberRole.PARENT);
        model.addAttribute("isStaff", role == MemberRole.PRINCIPAL || role == MemberRole.TEACHER);
        model.addAttribute("reviewQueueLabel", role == MemberRole.PRINCIPAL ? "원장 검토 큐" : "교사 검토 큐");
        return "attendance/requests";
    }
}
