package com.kinderp.domain.auth.controller;

import com.kinderp.domain.auth.dto.request.LoginRequest;
import com.kinderp.domain.auth.dto.request.SignUpRequest;
import com.kinderp.domain.auth.dto.response.AuthSessionResponse;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.member.dto.response.MemberResponse;
import com.kinderp.domain.auth.service.AuthService;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.ClientIpResolver;
import com.kinderp.global.security.jwt.JwtTokenProvider;
import com.kinderp.global.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 인증 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증, JWT refresh rotation, 활성 세션 관리 API")
public class AuthApiController {

    private static final String ACTIVE_SESSIONS_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": [
                {
                  "sessionId": "01JZ-DEMO",
                  "current": true,
                  "clientIp": "127.0.0.1",
                  "userAgent": "Mozilla/5.0",
                  "expiresAt": "2026-05-26T10:00:00"
                }
              ]
            }
            """;

    private final AuthService authService;
    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientIpResolver clientIpResolver;
    private final AuthAuditLogService authAuditLogService;

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signUp(@Valid @RequestBody SignUpRequest request,
                                                               HttpServletRequest httpServletRequest) {
        // 비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(
                            com.kinderp.global.exception.ErrorCode.INVALID_INPUT_VALUE,
                            "비밀번호가 일치하지 않습니다"
                    ));
        }

        // 회원가입
        Long memberId = authService.signUp(
                request.getEmail(),
                request.getPassword(),
                request.getName(),
                request.getPhone(),
                request.getRole(),
                clientIpResolver.resolve(httpServletRequest)
        );

        // 회원 정보 조회
        com.kinderp.domain.member.entity.Member member = memberService.getMemberById(memberId);

        return ResponseEntity
                .ok(ApiResponse.success(MemberResponse.from(member), "회원가입이 완료되었습니다"));
    }

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody LoginRequest request,
                                                    HttpServletRequest httpServletRequest,
                                                    HttpServletResponse response) {
        authService.login(
                request.getEmail(),
                request.getPassword(),
                clientIpResolver.resolve(httpServletRequest),
                httpServletRequest.getHeader("User-Agent"),
                response
        );

        return ResponseEntity
                .ok(ApiResponse.success(null, "로그인되었습니다"));
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request,
                                                     HttpServletResponse response) {
        authService.logout(getRefreshToken(request), response);

        return ResponseEntity
                .ok(ApiResponse.success(null, "로그아웃되었습니다"));
    }

    /**
     * Access Token 갱신
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request,
                                                      HttpServletResponse response) {
        // 쿠키에서 Refresh Token 추출
        String refreshToken = getRefreshToken(request);
        String clientIp = clientIpResolver.resolve(request);

        if (refreshToken == null) {
            authAuditLogService.recordRefreshFailure(null, null, clientIp, ErrorCode.TOKEN_INVALID.getCode());
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(ErrorCode.TOKEN_INVALID));
        }

        authService.refreshAccessToken(refreshToken, clientIp, request.getHeader("User-Agent"), response);

        return ResponseEntity
                .ok(ApiResponse.success(null, "토큰이 갱신되었습니다"));
    }

    /**
     * 현재 로그인한 회원 정보 조회
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MemberResponse>> getCurrentMember(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        com.kinderp.domain.member.entity.Member member = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());

        return ResponseEntity
                .ok(ApiResponse.success(MemberResponse.from(member)));
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "활성 세션 목록",
            description = "현재 사용자 refresh session registry에서 활성 기기 세션을 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "활성 세션 목록 응답",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ACTIVE_SESSIONS_RESPONSE_EXAMPLE)
                    )
            )
    )
    public ResponseEntity<ApiResponse<List<AuthSessionResponse>>> getActiveSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String currentSessionId = resolveCurrentSessionId(request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(
                authService.getActiveSessions(userDetails.getMemberId(), currentSessionId)
        ));
    }

    @DeleteMapping("/sessions/others")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "다른 기기 세션 종료",
            description = "현재 세션을 제외한 같은 사용자 refresh session을 Redis registry에서 제거합니다."
    )
    public ResponseEntity<ApiResponse<Void>> revokeOtherSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String currentSessionId = resolveCurrentSessionId(request, userDetails.getMemberId());
        authService.revokeOtherSessions(userDetails.getMemberId(), currentSessionId);
        return ResponseEntity.ok(ApiResponse.success(null, "다른 기기 세션을 종료했습니다"));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "선택 세션 종료",
            description = "지정한 sessionId를 즉시 revoke하고, 현재 세션이면 응답 쿠키도 정리합니다."
    )
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String sessionId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String currentSessionId = resolveCurrentSessionId(request, userDetails.getMemberId());
        authService.revokeSession(userDetails.getMemberId(), sessionId, currentSessionId, response);
        return ResponseEntity.ok(ApiResponse.success(null, "선택한 세션을 종료했습니다"));
    }

    /**
     * 쿠키에서 Refresh Token 추출
     */
    private String getRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (jwtTokenProvider.getRefreshTokenCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String resolveCurrentSessionId(HttpServletRequest request, Long expectedMemberId) {
        String accessToken = getCookieValue(request, jwtTokenProvider.getAccessTokenCookieName());
        String sessionId = extractSessionId(accessToken, expectedMemberId);
        if (sessionId != null) {
            return sessionId;
        }

        String refreshToken = getCookieValue(request, jwtTokenProvider.getRefreshTokenCookieName());
        sessionId = extractSessionId(refreshToken, expectedMemberId);
        if (sessionId != null) {
            return sessionId;
        }

        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    private String extractSessionId(String token, Long expectedMemberId) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return null;
        }

        Long memberId = jwtTokenProvider.getMemberId(token);
        if (memberId == null || !memberId.equals(expectedMemberId)) {
            return null;
        }
        return jwtTokenProvider.getSessionId(token);
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
