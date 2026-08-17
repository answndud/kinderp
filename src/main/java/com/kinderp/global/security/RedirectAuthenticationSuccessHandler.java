package com.kinderp.global.security;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.security.user.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedirectAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Member member = memberRepository.findById(userDetails.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found: " + userDetails.getMemberId()));

        String redirectUrl = determineRedirectUrl(member);
        log.info("Login successful for memberId={}, redirecting to={}", member.getId(), redirectUrl);

        response.sendRedirect(redirectUrl);
    }

    private String determineRedirectUrl(Member member) {
        // 원장 로직
        if (member.getRole() == MemberRole.PRINCIPAL) {
            if (member.getKindergarten() == null) {
                return "/kindergarten/create";
            }
            return "/";
        }

        // 교사 로직
        if (member.getRole() == MemberRole.TEACHER) {
            if (member.getStatus() == MemberStatus.PENDING || member.getKindergarten() == null) {
                return "/applications/pending";
            }
            return "/";
        }

        // 학부모 로직
        if (member.getRole() == MemberRole.PARENT) {
            if (member.getStatus() == MemberStatus.PENDING || member.getKindergarten() == null) {
                return "/applications/pending";
            }
            return "/";
        }

        // 기본: 홈
        return "/";
    }
}
