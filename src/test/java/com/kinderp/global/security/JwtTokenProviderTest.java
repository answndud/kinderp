package com.kinderp.global.security;

import com.kinderp.global.security.jwt.JwtProperties;
import com.kinderp.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
@DisplayName("JwtTokenProvider 파싱")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-at-least-32-bytes-long!!");
        provider = new JwtTokenProvider(properties);
        provider.init();
    }

    @Test
    @DisplayName("access 토큰은 한 번의 파싱으로 회원·세션·타입 claim을 돌려준다")
    void parseAccessToken() {
        String token = provider.createAccessToken(7L, "a@test.com", "ROLE_PARENT", "session-1");

        var claims = provider.parseToken(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().memberId()).isEqualTo(7L);
        assertThat(claims.get().email()).isEqualTo("a@test.com");
        assertThat(claims.get().sessionId()).isEqualTo("session-1");
        assertThat(claims.get().isAccessToken()).isTrue();
    }

    @Test
    @DisplayName("refresh 토큰은 access 토큰으로 취급되지 않는다")
    void refreshTokenIsNotAccessToken() {
        String token = provider.createRefreshToken(7L, "a@test.com", "ROLE_PARENT", "session-1");

        assertThat(provider.parseToken(token)).get().extracting(c -> c.isAccessToken()).isEqualTo(false);
    }

    @Test
    @DisplayName("변조되거나 형식이 깨진 토큰은 빈 결과다")
    void tamperedOrMalformedToken_IsEmpty() {
        String token = provider.createAccessToken(7L, "a@test.com", "ROLE_PARENT", "session-1");

        assertThat(provider.parseToken(token + "x")).isEmpty();
        assertThat(provider.parseToken("not-a-jwt")).isEmpty();
        assertThat(provider.parseToken("")).isEmpty();
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.validateToken(token + "x")).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 빈 결과다")
    void expiredToken_IsEmpty() {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret("unit-test-secret-key-at-least-32-bytes-long!!");
        shortLived.setAccessTokenValidity(-1000L);
        JwtTokenProvider expiring = new JwtTokenProvider(shortLived);
        expiring.init();

        String token = expiring.createAccessToken(7L, "a@test.com", "ROLE_PARENT", "session-1");

        assertThat(expiring.parseToken(token)).isEmpty();
    }
}
