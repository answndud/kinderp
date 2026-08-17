package com.kinderp.global.security.jwt;

import com.kinderp.domain.auth.service.AuthSessionRegistryService;
import com.kinderp.global.security.ClientIpResolver;
import com.kinderp.global.security.user.CustomUserDetails;
import com.kinderp.global.security.user.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.GenericFilterBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JWT 인증 필터
 * 요청에서 JWT 토큰을 추출하고 검증한 후 SecurityContext에 인증 정보 설정
 */
public class JwtFilter extends GenericFilterBean {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthSessionRegistryService authSessionRegistryService;
    private final ClientIpResolver clientIpResolver;

    public JwtFilter(JwtTokenProvider jwtTokenProvider,
                     CustomUserDetailsService userDetailsService,
                     AuthSessionRegistryService authSessionRegistryService,
                     ClientIpResolver clientIpResolver) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.authSessionRegistryService = authSessionRegistryService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 쿠키에서 Access Token 추출
        String token = resolveToken(httpRequest);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                Long memberId = jwtTokenProvider.getMemberId(token);
                String sessionId = jwtTokenProvider.getSessionId(token);
                if (memberId == null || sessionId == null || sessionId.isBlank()) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }
                if (!authSessionRegistryService.isSessionActive(memberId, sessionId)) {
                    SecurityContextHolder.clearContext();
                    log.debug("JWT 인증 실패 - 비활성 세션: memberId={}, sessionId={}", memberId, sessionId);
                    chain.doFilter(request, response);
                    return;
                }

                Authentication authentication = getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (shouldTrackSessionActivity(httpRequest)) {
                    authSessionRegistryService.touchSession(
                            memberId,
                            sessionId,
                            clientIpResolver.resolve(httpRequest),
                            httpRequest.getHeader("User-Agent")
                    );
                }
                log.debug("JWT 인증 성공: memberId={}", memberId);
            } catch (UsernameNotFoundException e) {
                SecurityContextHolder.clearContext();
                log.debug("JWT 인증 실패 - 사용자 조회 불가: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 쿠키에서 Access Token 추출
     */
    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (jwtTokenProvider.getAccessTokenCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    /**
     * 토큰으로 Authentication 객체 생성
     */
    private Authentication getAuthentication(String token) {
        String email = jwtTokenProvider.getEmail(token);

        // DB에서 회원 조회 (상태 확인을 위해)
        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(email);

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    private boolean shouldTrackSessionActivity(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return true;
        }

        return !uri.startsWith("/css/")
                && !uri.startsWith("/js/")
                && !uri.startsWith("/img/")
                && !uri.startsWith("/images/")
                && !uri.startsWith("/favicon.ico")
                && !uri.startsWith("/swagger-ui")
                && !uri.startsWith("/v3/api-docs")
                && !uri.startsWith("/actuator");
    }
}
