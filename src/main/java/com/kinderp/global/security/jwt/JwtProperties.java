package com.kinderp.global.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 설정 Properties
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 서명 키 (256비트 이상)
     */
    private String secret;

    /**
     * Access Token 유효기간 (밀리초)
     */
    private Long accessTokenValidity = 900000L; // 15분

    /**
     * Refresh Token 유효기간 (밀리초)
     */
    private Long refreshTokenValidity = 604800000L; // 7일

    /**
     * Access Token 쿠키 이름
     */
    private String accessTokenCookieName = "access_token";

    /**
     * Refresh Token 쿠키 이름
     */
    private String refreshTokenCookieName = "refresh_token";

    /**
     * JWT 쿠키 Secure 플래그
     */
    private boolean cookieSecure = true;

    /**
     * JWT 쿠키 SameSite 속성
     */
    private String cookieSameSite = "Strict";
}
