package com.kinderp.global.config;

import com.kinderp.domain.member.dto.response.MemberResponse;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.security.ManagementSurfaceProperties;
import com.kinderp.global.security.AuthenticatedMemberResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 전역 컨트롤러 어드바이스
 * 모든 컨트롤러에 공통으로 적용되는 설정
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final AuthenticatedMemberResolver authenticatedMemberResolver;
    private final ManagementSurfaceProperties managementSurfaceProperties;

    /**
     * 현재 로그인한 회원 정보를 모든 뷰에 전달
     */
    @ModelAttribute("currentMember")
    public MemberResponse currentMember(Authentication authentication) {
        Member resolvedMember = authenticatedMemberResolver.resolve(authentication).orElse(null);
        if (resolvedMember == null) {
            log.debug("currentMember: userDetails is null");
            return null;
        }

        // AuthenticatedMemberResolver가 유치원까지 fetch한 동일 엔티티를 재사용해 중복 조회를 피한다.
        return MemberResponse.from(resolvedMember);
    }

    @ModelAttribute("publicApiDocsEnabled")
    public boolean publicApiDocsEnabled() {
        return managementSurfaceProperties.isPublicApiDocs();
    }

    @ModelAttribute("publicPrometheusEnabled")
    public boolean publicPrometheusEnabled() {
        return managementSurfaceProperties.isExposePrometheusOnAppPort();
    }
}
