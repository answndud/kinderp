package com.kinderp.global.security;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleRedirectInterceptor implements HandlerInterceptor {

    private final AuthenticatedMemberResolver authenticatedMemberResolver;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String uri = request.getRequestURI();

        // 정적 리소스는 통과
        if (isStaticResource(uri)) {
            return true;
        }

        // 운영/문서 경로는 통과
        if (isInfrastructurePath(uri)) {
            return true;
        }

        // API 엔드포인트는 통과 (Controller에서 권한 체크)
        if (isApiPath(uri)) {
            return true;
        }

        // 인증 확인 (홈 페이지 제외)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());

        // 홈("/")은 비로그인 사용자만 통과 (로그인 사용자는 아래 강제 리다이렉트 적용)
        if (uri.equals("/") && !isAuthenticated) {
            return true;
        }

        // 로그인/회원가입 페이지는 인증 없이 통과
        if (isPublicPath(uri)) {
            // 이미 로그인된 사용자가 로그인 페이지로 오면 홈으로 리다이렉트
            if (isAuthenticated && (uri.startsWith("/login") || uri.startsWith("/signup"))) {
                response.sendRedirect("/");
                return false;
            }
            return true;
        }

        // 인증되지 않은 사용자는 로그인 페이지로
        if (!isAuthenticated) {
            response.sendRedirect("/login");
            return false;
        }

        Member member = authenticatedMemberResolver.resolve(auth).orElse(null);

        if (member == null) {
            response.sendRedirect("/login");
            return false;
        }

        // 강제 리다이렉트 로직
        String redirectUrl = shouldForceRedirect(member, uri);
        if (redirectUrl != null) {
            log.debug("Forcing redirect for memberId={} from={} to={}", member.getId(), uri, redirectUrl);
            response.sendRedirect(redirectUrl);
            return false;
        }

        return true;
    }

    private boolean isPublicPath(String uri) {
        return uri.startsWith("/login") ||
                uri.startsWith("/signup") ||
                uri.startsWith("/error");
    }

    private boolean isStaticResource(String uri) {
        return uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.startsWith("/images/") ||
                uri.startsWith("/favicon.ico") ||
                uri.endsWith(".css") ||
                uri.endsWith(".js") ||
                uri.endsWith(".png") ||
                uri.endsWith(".jpg") ||
                uri.endsWith(".jpeg") ||
                uri.endsWith(".gif") ||
                uri.endsWith(".svg") ||
                uri.endsWith(".ico");
    }

    private boolean isApiPath(String uri) {
        return uri.startsWith("/api/");
    }

    private boolean isInfrastructurePath(String uri) {
        return uri.startsWith("/swagger-ui") ||
                uri.startsWith("/v3/api-docs") ||
                uri.startsWith("/actuator/health") ||
                uri.startsWith("/actuator/info") ||
                uri.startsWith("/actuator/prometheus");
    }

    private String shouldForceRedirect(Member member, String uri) {
        // 알림 프래그먼트는 어디서든 허용 (대기 화면에서도 사용)
        if (uri.startsWith("/notifications/fragments/")) {
            return null;
        }

        // PENDING 상태: 대기 페이지로 강제 이동 (선생님/학부모)
        if ((member.getRole() == MemberRole.TEACHER || member.getRole() == MemberRole.PARENT) &&
                member.getStatus() == MemberStatus.PENDING &&
                !uri.startsWith("/applications/pending")) {
            return "/applications/pending";
        }

        // 원장: 유치원 없으면 생성 페이지로 강제 이동
        if (member.getRole() == MemberRole.PRINCIPAL &&
                member.getKindergarten() == null &&
                !uri.startsWith("/kindergarten/create")) {
            return "/kindergarten/create";
        }

        // 교사: 유치원 미배정이면 신청 페이지로 강제 이동
        if (member.getRole() == MemberRole.TEACHER &&
                member.getKindergarten() == null &&
                !uri.startsWith("/applications/pending")) {
            return "/applications/pending";
        }

        // 학부모: 유치원 미배정이면 신청 페이지로 강제 이동
        if (member.getRole() == MemberRole.PARENT &&
                member.getKindergarten() == null &&
                !uri.startsWith("/applications/pending")) {
            return "/applications/pending";
        }

        return null;
    }
}
