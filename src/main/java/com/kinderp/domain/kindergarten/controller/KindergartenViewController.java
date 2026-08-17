package com.kinderp.domain.kindergarten.controller;

import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 유치원 뷰 컨트롤러
 */
@Controller
@RequestMapping("/kindergarten")
@RequiredArgsConstructor
public class KindergartenViewController {

    /**
     * 유치원 생성 페이지 (원장용)
     */
    @GetMapping("/create")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public String createKindergartenPage(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return "kindergarten/create";
    }

    /**
     * 유치원 선택 페이지 (교사용)
     */
    @GetMapping("/select")
    @PreAuthorize("hasRole('TEACHER')")
    public String selectKindergartenPage(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return "kindergarten/select";
    }
}
