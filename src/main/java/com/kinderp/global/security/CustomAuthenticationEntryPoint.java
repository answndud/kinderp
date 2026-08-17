package com.kinderp.global.security;

import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 요청에 대한 진입점 처리
 * 뷰 페이지 요청의 경우 로그인 페이지로 리다이렉트
 * API 요청의 경우 401 Unauthorized 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        String requestURI = request.getRequestURI();

        // API 요청인 경우 JSON 401 응답
        if (requestURI.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS)));
            return;
        }

        // 뷰 페이지 요청인 경우 로그인 페이지로 리다이렉트
        log.debug("인증되지 않은 요청: {} -> 로그인 페이지로 리다이렉트", requestURI);
        response.sendRedirect("/login");
    }
}
