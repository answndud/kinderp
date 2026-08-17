package com.kinderp.domain.classroom.controller;

import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ClassroomViewController {

    @GetMapping("/classrooms")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String classroomsPage(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return "classroom/classrooms";
    }
}
