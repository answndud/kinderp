package com.kinderp.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 토큰 생성 및 검증
 */
@Slf4j
@Getter
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 시크릿 키 초기화
     */
    @PostConstruct
    public void init() {
        // 256비트 이상의 키로 HMAC-SHA 알고리즘용 키 생성
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access Token 생성
     */
    public String createAccessToken(Long memberId, String email, String role, String sessionId) {
        return createToken(memberId, email, role, sessionId, "access", jwtProperties.getAccessTokenValidity());
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(Long memberId, String email, String role, String sessionId) {
        return createToken(memberId, email, role, sessionId, "refresh", jwtProperties.getRefreshTokenValidity());
    }

    /**
     * JWT 토큰 생성
     */
    private String createToken(Long memberId, String email, String role, String sessionId, String tokenType, long validity) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + validity);

        return Jwts.builder()
                .subject(email)
                .claim("memberId", memberId)
                .claim("role", role)
                .claim("sessionId", sessionId)
                .claim("tokenType", tokenType)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰에서 이메일 추출
     */
    public String getEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 토큰에서 역할 추출
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * 토큰에서 회원 ID 추출
     */
    public Long getMemberId(String token) {
        Number memberId = getClaims(token).get("memberId", Number.class);
        return memberId != null ? memberId.longValue() : null;
    }

    /**
     * 토큰에서 세션 ID 추출
     */
    public String getSessionId(String token) {
        return getClaims(token).get("sessionId", String.class);
    }

    /**
     * 토큰 타입 추출
     */
    public String getTokenType(String token) {
        return getClaims(token).get("tokenType", String.class);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    /**
     * 토큰 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 토큰 만료 여부 확인
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * 토큰에서 Claims 추출
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰의 남은 유효시간(밀리초)
     */
    public long getRemainingValidity(String token) {
        Date expiration = getClaims(token).getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }

    /**
     * Access Token 쿠키 이름
     */
    public String getAccessTokenCookieName() {
        return jwtProperties.getAccessTokenCookieName();
    }

    /**
     * Refresh Token 쿠키 이름
     */
    public String getRefreshTokenCookieName() {
        return jwtProperties.getRefreshTokenCookieName();
    }

    /**
     * Access Token 유효기간 (밀리초)
     */
    public long getAccessTokenValidity() {
        return jwtProperties.getAccessTokenValidity();
    }

    /**
     * Refresh Token 유효기간 (밀리초)
     */
    public long getRefreshTokenValidity() {
        return jwtProperties.getRefreshTokenValidity();
    }
}
