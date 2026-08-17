package com.kinderp.domain.auth.service;

import com.kinderp.domain.auth.dto.response.AuthSessionResponse;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.authaudit.service.AuthAnomalyAlertService;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.jwt.JwtProperties;
import com.kinderp.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 인증 서비스
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final AuthSessionRegistryService authSessionRegistryService;
    private final AuthLoginBootstrapService authLoginBootstrapService;
    private final AuthRateLimitService authRateLimitService;
    private final AuthAuditLogService authAuditLogService;
    private final AuthAnomalyAlertService authAnomalyAlertService;

    /**
     * 회원가입
     */
    @Transactional
    public Long signUp(String email, String password, String name, String phone, String role, String clientIp) {
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "역할은 필수입니다");
        }

        com.kinderp.domain.member.entity.MemberRole memberRole;
        try {
            memberRole = com.kinderp.domain.member.entity.MemberRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "역할 값이 올바르지 않습니다");
        }

        if (memberRole == com.kinderp.domain.member.entity.MemberRole.PRINCIPAL) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_ROLE, "원장 계정은 공개 회원가입으로 만들 수 없습니다");
        }

        authRateLimitService.validateSignupAllowed(clientIp);
        return memberService.signUp(email, password, name, phone, memberRole);
    }

    /**
     * 로그인
     */
    public void login(String email, String password, String clientIp, String userAgent, HttpServletResponse response) {
        try {
            authRateLimitService.validateLoginAllowed(clientIp, email);

            // 1. 인증 시도
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            authLoginBootstrapService.afterAuthenticated(email);
            authRateLimitService.clearLoginFailures(email);
            authAnomalyAlertService.clearLoginFailureCounter(email);

            // 2. 회원 정보 조회
            Member member = memberService.getMemberByEmail(email);
            issueTokens(member, MemberAuthProvider.LOCAL, clientIp, userAgent, response);
            authAuditLogService.recordLoginSuccess(
                    member.getId(),
                    member.getEmail(),
                    MemberAuthProvider.LOCAL,
                    clientIp
            );

        } catch (AuthenticationException e) {
            authRateLimitService.recordLoginFailure(clientIp, email);
            authAuditLogService.recordLoginFailure(
                    email,
                    MemberAuthProvider.LOCAL,
                    clientIp,
                    ErrorCode.INVALID_CREDENTIALS.getCode()
            );
            authAnomalyAlertService.alertRepeatedLoginFailuresIfNeeded(email, clientIp);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        } catch (BusinessException e) {
            authAuditLogService.recordLoginFailure(
                    email,
                    MemberAuthProvider.LOCAL,
                    clientIp,
                    e.getErrorCode().getCode()
            );
            authAnomalyAlertService.alertRepeatedLoginFailuresIfNeeded(email, clientIp);
            throw e;
        }
    }

    public void loginBySocial(Member member,
                              MemberAuthProvider provider,
                              String clientIp,
                              String userAgent,
                              HttpServletResponse response) {
        authLoginBootstrapService.afterAuthenticated(member.getEmail());
        issueTokens(member, provider, clientIp, userAgent, response);
    }

    /**
     * 로그아웃
     */
    public void logout(String refreshToken, HttpServletResponse response) {
        revokeSession(refreshToken);
        expireCookie(response, jwtTokenProvider.getAccessTokenCookieName());
        expireCookie(response, jwtTokenProvider.getRefreshTokenCookieName());
    }

    /**
     * Access Token 갱신
     */
    public void refreshAccessToken(String refreshToken, String clientIp, String userAgent, HttpServletResponse response) {
        Long memberId = null;
        String email = null;

        try {
            authRateLimitService.validateRefreshAllowed(clientIp);

            TokenSessionClaims claims = extractRefreshTokenClaims(refreshToken);
            memberId = claims.memberId();
            email = claims.email();

            String storedToken = authSessionRegistryService.getStoredRefreshToken(claims.memberId(), claims.sessionId())
                    .orElse(null);
            if (storedToken == null) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            }
            if (!storedToken.equals(refreshToken)) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            }

            Member member = memberService.getMemberById(claims.memberId());
            validateRefreshTargetMember(member, claims.email());

            String newAccessToken = jwtTokenProvider.createAccessToken(
                    member.getId(),
                    member.getEmail(),
                    member.getRole().getKey(),
                    claims.sessionId()
            );
            String newRefreshToken = jwtTokenProvider.createRefreshToken(
                    member.getId(),
                    member.getEmail(),
                    member.getRole().getKey(),
                    claims.sessionId()
            );

            authSessionRegistryService.rotateSession(
                    member.getId(),
                    claims.sessionId(),
                    newRefreshToken,
                    Math.max(jwtTokenProvider.getRemainingValidity(newRefreshToken), 1L),
                    null,
                    clientIp,
                    userAgent
            );

            addCookie(response, jwtTokenProvider.getAccessTokenCookieName(), newAccessToken,
                    (int) (jwtTokenProvider.getAccessTokenValidity() / 1000));
            addCookie(response, jwtTokenProvider.getRefreshTokenCookieName(), newRefreshToken,
                    (int) (jwtTokenProvider.getRefreshTokenValidity() / 1000));
            authAuditLogService.recordRefreshSuccess(member.getId(), member.getEmail(), clientIp);
        } catch (BusinessException e) {
            authAuditLogService.recordRefreshFailure(memberId, email, clientIp, e.getErrorCode().getCode());
            throw e;
        }
    }

    public void revokeAllSessions(Long memberId, HttpServletResponse response) {
        revokeAllSessions(memberId);
        expireCookie(response, jwtTokenProvider.getAccessTokenCookieName());
        expireCookie(response, jwtTokenProvider.getRefreshTokenCookieName());
    }

    public List<AuthSessionResponse> getActiveSessions(Long memberId, String currentSessionId) {
        return authSessionRegistryService.getActiveSessions(memberId, currentSessionId);
    }

    public void revokeSession(Long memberId, String sessionId, String currentSessionId, HttpServletResponse response) {
        authSessionRegistryService.revokeSession(memberId, sessionId);
        if (sessionId != null && sessionId.equals(currentSessionId)) {
            expireCookie(response, jwtTokenProvider.getAccessTokenCookieName());
            expireCookie(response, jwtTokenProvider.getRefreshTokenCookieName());
        }
    }

    public void revokeOtherSessions(Long memberId, String currentSessionId) {
        authSessionRegistryService.revokeOtherSessions(memberId, currentSessionId);
    }

    /**
     * 쿠키 추가
     */
    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(jwtProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", jwtProperties.getCookieSameSite());
        response.addCookie(cookie);
    }

    /**
     * 쿠키 만료
     */
    private void expireCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(jwtProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", jwtProperties.getCookieSameSite());
        response.addCookie(cookie);
    }

    private void issueTokens(Member member,
                             MemberAuthProvider provider,
                             String clientIp,
                             String userAgent,
                             HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtTokenProvider.createAccessToken(
                member.getId(),
                member.getEmail(),
                member.getRole().getKey(),
                sessionId
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(
                member.getId(),
                member.getEmail(),
                member.getRole().getKey(),
                sessionId
        );

        authSessionRegistryService.registerSession(
                member.getId(),
                sessionId,
                refreshToken,
                Math.max(jwtTokenProvider.getRemainingValidity(refreshToken), 1L),
                provider,
                clientIp,
                userAgent
        );

        addCookie(response, jwtTokenProvider.getAccessTokenCookieName(), accessToken,
                (int) (jwtTokenProvider.getAccessTokenValidity() / 1000));
        addCookie(response, jwtTokenProvider.getRefreshTokenCookieName(), refreshToken,
                (int) (jwtTokenProvider.getRefreshTokenValidity() / 1000));
    }

    private void revokeSession(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return;
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        String sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (memberId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }

        authSessionRegistryService.revokeSession(memberId, sessionId);
    }

    private void revokeAllSessions(Long memberId) {
        authSessionRegistryService.revokeAllSessions(memberId);
    }

    private TokenSessionClaims extractRefreshTokenClaims(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        String email = jwtTokenProvider.getEmail(refreshToken);
        String sessionId = jwtTokenProvider.getSessionId(refreshToken);

        if (memberId == null || email == null || email.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        return new TokenSessionClaims(memberId, email, sessionId);
    }

    private void validateRefreshTargetMember(Member member, String email) {
        if (!member.getEmail().equals(email)
                || member.getDeletedAt() != null
                || member.getStatus() == MemberStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    private record TokenSessionClaims(Long memberId, String email, String sessionId) {
    }
}
