package com.erp.api;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.auth.service.AuthSessionRegistryService;
import com.erp.domain.authaudit.entity.AuthAuditEventType;
import com.erp.domain.authaudit.entity.AuthAuditResult;
import com.erp.domain.authaudit.repository.AuthAuditLogRepository;
import com.erp.domain.member.entity.MemberAuthProvider;
import com.erp.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 인증 API 통합 테스트
 */
@DisplayName("인증 API 테스트")
@Tag("integration")
class AuthApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    @AfterEach
    void cleanupRedisState() {
        clearRedis();
    }

    @Test
    @DisplayName("인증되지 않은 API 요청 - 401 공통 응답 포맷 반환")
    void unauthorizedApiRequest_ReturnsStandardErrorResponse() throws Exception {
        clearAuthentication();

        mockMvc.perform(get("/api/v1/attendance/1"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Nested
    @DisplayName("회원가입 API")
    class SignUpApiTest {

        @Test
        @DisplayName("회원가입 - 성공 (학부모)")
        void signup_Success_Parent() throws Exception {
            String requestBody = """
                    {
                        "email": "newparent@test.com",
                        "password": "Test1234!",
                        "passwordConfirm": "Test1234!",
                        "name": "새학부모",
                        "birthDate": "1990-01-01",
                        "phone": "01012345678",
                        "role": "PARENT"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("newparent@test.com"))
                    .andExpect(jsonPath("$.data.name").value("새학부모"));
        }

        @Test
        @DisplayName("회원가입 - 같은 IP에서 시간당 10회를 초과하면 차단")
        void signup_Fail_RateLimited_ByIp() throws Exception {
            for (int attempt = 0; attempt < 10; attempt++) {
                String requestBody = """
                        {
                            "email": "signup-rate-%d@test.com",
                            "password": "Test1234!",
                            "passwordConfirm": "Test1234!",
                            "name": "가입테스트%d",
                            "birthDate": "1990-01-01",
                            "phone": "0101234%04d",
                            "role": "PARENT"
                        }
                        """.formatted(attempt, attempt, attempt);

                mockMvc.perform(post("/api/v1/auth/signup")
                                .with(csrf())
                                .with(remoteAddr("198.51.100.120"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(post("/api/v1/auth/signup")
                            .with(csrf())
                            .with(remoteAddr("198.51.100.120"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "email": "signup-rate-blocked@test.com",
                                        "password": "Test1234!",
                                        "passwordConfirm": "Test1234!",
                                        "name": "차단테스트",
                                        "birthDate": "1990-01-01",
                                        "phone": "01099999999",
                                        "role": "PARENT"
                                    }
                                    """))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().string("Retry-After", "60"))
                    .andExpect(jsonPath("$.code").value("A006"));
        }

        @Test
        @DisplayName("회원가입 - 실패 (중복 이메일)")
        void signup_Fail_DuplicateEmail() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "Test1234!",
                        "passwordConfirm": "Test1234!",
                        "name": "중복학부모",
                        "birthDate": "1990-01-01",
                        "phone": "01012345678",
                        "role": "PARENT"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("M001"));
        }

        @Test
        @DisplayName("회원가입 - 실패 (유효하지 않은 입력)")
        void signup_Fail_InvalidInput() throws Exception {
            String requestBody = """
                    {
                        "email": "invalid-email",
                        "password": "123",
                        "name": "",
                        "birthDate": "1990-01-01",
                        "phone": "010-1234-5678",
                        "role": "PARENT"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("회원가입 - 공개 요청으로 원장 계정을 만들 수 없다")
        void signup_Fail_PrincipalRoleIsNotPubliclyAssignable() throws Exception {
            String requestBody = """
                    {
                        "email": "attacker-principal@test.com",
                        "password": "Test1234!",
                        "passwordConfirm": "Test1234!",
                        "name": "공개원장",
                        "phone": "01012345678",
                        "role": "PRINCIPAL"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("AP008"));
        }
    }

    @Nested
    @DisplayName("로그인 API")
    class LoginApiTest {

        @Test
        @DisplayName("로그인 - 성공")
        void login_Success() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "test1234"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            var auditLogs = readCommitted(authAuditLogRepository::findAllByCreatedAtAsc);
            assertThat(auditLogs).isNotEmpty();
            assertThat(auditLogs.get(auditLogs.size() - 1))
                    .extracting(
                            auditLog -> auditLog.getEventType(),
                            auditLog -> auditLog.getResult(),
                            auditLog -> auditLog.getProvider(),
                            auditLog -> auditLog.getEmail()
                    )
                    .containsExactly(
                            AuthAuditEventType.LOGIN,
                            AuthAuditResult.SUCCESS,
                            MemberAuthProvider.LOCAL,
                            "parent@test.com"
                    );
        }

        @Test
        @DisplayName("로그인 - 실패 (잘못된 비밀번호)")
        void login_Fail_WrongPassword() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A001"));

            var auditLogs = readCommitted(authAuditLogRepository::findAllByCreatedAtAsc);
            assertThat(auditLogs).isNotEmpty();
            assertThat(auditLogs.get(auditLogs.size() - 1))
                    .extracting(
                            auditLog -> auditLog.getEventType(),
                            auditLog -> auditLog.getResult(),
                            auditLog -> auditLog.getProvider(),
                            auditLog -> auditLog.getEmail(),
                            auditLog -> auditLog.getReason()
                    )
                    .containsExactly(
                            AuthAuditEventType.LOGIN,
                            AuthAuditResult.FAILURE,
                            MemberAuthProvider.LOCAL,
                            "parent@test.com",
                            "A001"
                    );
        }

        @Test
        @DisplayName("로그인 - 성공 (헤더 기반 CSRF 토큰)")
        void login_Success_WithHeaderCsrfToken() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "test1234"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("로그인 - 실패 (존재하지 않는 이메일)")
        void login_Fail_EmailNotFound() throws Exception {
            String requestBody = """
                    {
                        "email": "notfound@test.com",
                        "password": "test1234"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A001"));
        }

        @Test
        @DisplayName("로그인 - 실패 (CSRF 토큰 없음)")
        void login_Fail_WithoutCsrfToken() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "test1234"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("로그인 - 반복 성공은 rate limit에 누적되지 않는다")
        void login_Success_RepeatedSuccessfulLoginsAreNotRateLimited() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "test1234"
                    }
                    """;

            for (int attempt = 0; attempt < 6; attempt++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("203.0.113.40"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true));
            }
        }

        @Test
        @DisplayName("로그인 - 반복 실패 시 이메일 기준 rate limit 초과")
        void login_Fail_RateLimited_ByEmail() throws Exception {
            String requestBody = """
                    {
                        "email": "parent@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            for (int attempt = 0; attempt < 5; attempt++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("203.0.113.10"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(remoteAddr("203.0.113.10"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().string("Retry-After", "60"))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A006"));
        }

        @Test
        @DisplayName("로그인 - 기존 계정에서 반복 실패가 발생하면 원장에게 이상 징후 알림을 한 번만 보낸다")
        void login_Fail_RepeatedFailures_SendSinglePrincipalAlert() throws Exception {
            String requestBody = """
                    {
                        "email": "teacher@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            for (int attempt = 0; attempt < 5; attempt++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("203.0.113.88"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            mockMvc.perform(get("/api/v1/notifications")
                            .with(authenticated(principalMember))
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].type").value("AUTH_ANOMALY_DETECTED"))
                    .andExpect(jsonPath("$.data[0].title").value("인증 이상 징후 감지"))
                    .andExpect(jsonPath("$.data[0].content").value(org.hamcrest.Matchers.containsString("teacher@test.com")))
                    .andExpect(jsonPath("$.data[0].linkUrl").value(org.hamcrest.Matchers.containsString("email=teacher@test.com")));
        }

        @Test
        @DisplayName("로그인 - 존재하지 않는 이메일 반복 실패는 원장 이상 징후 알림을 만들지 않는다")
        void login_Fail_UnknownEmail_DoesNotSendPrincipalAlert() throws Exception {
            String requestBody = """
                    {
                        "email": "unknown-alert@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            for (int attempt = 0; attempt < 3; attempt++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("203.0.113.99"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            mockMvc.perform(get("/api/v1/notifications")
                            .with(authenticated(principalMember))
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("로그인 - 성공 후 이메일 실패 카운터가 초기화된다")
        void login_Success_ClearsEmailFailureCounter() throws Exception {
            String wrongPasswordBody = """
                    {
                        "email": "parent@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            for (int attempt = 0; attempt < 4; attempt++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("203.0.113.55"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongPasswordBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            String successBody = """
                    {
                        "email": "parent@test.com",
                        "password": "test1234"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(remoteAddr("203.0.113.55"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(successBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            for (int attempt = 0; attempt < 5; attempt++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("203.0.113.56"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongPasswordBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(remoteAddr("203.0.113.56"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody))
                    .andDo(print())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A006"));
        }

        @Test
        @DisplayName("로그인 - 신뢰하지 않는 remoteAddr의 forwarded 헤더는 rate limit 우회에 사용되지 않는다")
        void login_Fail_RateLimited_IgnoresForwardedHeaderFromUntrustedRemote() throws Exception {
            for (int attempt = 0; attempt < 15; attempt++) {
                String requestBody = """
                        {
                            "email": "spoof-%d@test.com",
                            "password": "wrongpassword"
                        }
                        """.formatted(attempt);

                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("198.51.100.10"))
                                .with(header("X-Forwarded-For", "203.0.113.%d".formatted(attempt + 1)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            String finalRequestBody = """
                    {
                        "email": "spoof-final@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(remoteAddr("198.51.100.10"))
                            .with(header("X-Forwarded-For", "203.0.113.250"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(finalRequestBody))
                    .andDo(print())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A006"));
        }

        @Test
        @DisplayName("로그인 - loopback 프록시에서는 forwarded 헤더를 client IP로 사용한다")
        void login_Fail_RateLimitUsesForwardedHeaderForTrustedLoopbackProxy() throws Exception {
            for (int attempt = 0; attempt < 15; attempt++) {
                String requestBody = """
                        {
                            "email": "trusted-proxy-%d@test.com",
                            "password": "wrongpassword"
                        }
                        """.formatted(attempt);

                mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                                .with(remoteAddr("127.0.0.1"))
                                .with(header("X-Forwarded-For", "203.0.113.77"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("A001"));
            }

            String rotatedClientBody = """
                    {
                        "email": "trusted-proxy-new-client@test.com",
                        "password": "wrongpassword"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(remoteAddr("127.0.0.1"))
                            .with(header("X-Forwarded-For", "203.0.113.78"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rotatedClientBody))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A001"));
        }
    }

    @Nested
    @DisplayName("토큰 갱신 API")
    class TokenRefreshApiTest {

        @Test
        @DisplayName("토큰 갱신 - 성공 시 세션별 Redis 키에 저장되고 rotation 된다")
        void refreshToken_Success_WithRotation() throws Exception {
            LoginCookies loginCookies = loginAsParent();
            String sessionKey = getRefreshSessionKey(loginCookies.refreshCookie());
            String sessionSetKey = getRefreshSessionSetKey(loginCookies.refreshCookie());
            String originalSessionId = jwtTokenProvider.getSessionId(loginCookies.refreshCookie().getValue());

            assertNotNull(loginCookies.refreshCookie());
            assertThat(redisTemplate.opsForValue().get(sessionKey)).isEqualTo(loginCookies.refreshCookie().getValue());
            assertThat(redisTemplate.opsForSet().members(sessionSetKey))
                    .contains(originalSessionId);

            MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .cookie(loginCookies.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
            Cookie rotatedAccessCookie = refreshResult.getResponse().getCookie("access_token");

            assertNotNull(rotatedRefreshCookie);
            assertNotNull(rotatedAccessCookie);
            assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(loginCookies.refreshCookie().getValue());
            assertThat(jwtTokenProvider.getSessionId(rotatedRefreshCookie.getValue())).isEqualTo(originalSessionId);
            assertThat(redisTemplate.opsForValue().get(sessionKey)).isEqualTo(rotatedRefreshCookie.getValue());
        }

        @Test
        @DisplayName("토큰 갱신 - 실패 (Redis 토큰 불일치)")
        void refreshToken_Fail_TokenMismatch() throws Exception {
            LoginCookies loginCookies = loginAsParent();

            assertNotNull(loginCookies.refreshCookie());
            redisTemplate.opsForValue().set(getRefreshSessionKey(loginCookies.refreshCookie()), "mismatch-token");

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .cookie(loginCookies.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A005"));

            var auditLogs = readCommitted(authAuditLogRepository::findAllByCreatedAtAsc);
            assertThat(auditLogs).isNotEmpty();
            assertThat(auditLogs.get(auditLogs.size() - 1))
                    .extracting(
                            auditLog -> auditLog.getEventType(),
                            auditLog -> auditLog.getResult(),
                            auditLog -> auditLog.getEmail(),
                            auditLog -> auditLog.getReason()
                    )
                    .containsExactly(
                            AuthAuditEventType.REFRESH,
                            AuthAuditResult.FAILURE,
                            "parent@test.com",
                            "A005"
                    );
        }

        @Test
        @DisplayName("토큰 갱신 - 반복 호출 시 IP 기준 rate limit 초과")
        void refreshToken_Fail_RateLimited_ByIp() throws Exception {
            LoginCookies loginCookies = loginAsParent("198.51.100.77");

            for (int attempt = 0; attempt < 10; attempt++) {
                MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                                .with(csrf())
                                .with(remoteAddr("198.51.100.77"))
                                .cookie(loginCookies.refreshCookie()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andReturn();

                loginCookies = new LoginCookies(
                        refreshResult.getResponse().getCookie("access_token"),
                        refreshResult.getResponse().getCookie("refresh_token")
                );
            }

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .with(remoteAddr("198.51.100.77"))
                            .cookie(loginCookies.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("A006"));
        }

        @Test
        @DisplayName("로그아웃 - 현재 세션만 정리하고 다른 기기 세션은 유지한다")
        void logout_Success_RevokesOnlyCurrentSession() throws Exception {
            LoginCookies firstLogin = loginAsParent();
            LoginCookies secondLogin = loginAsParent();

            String firstSessionKey = getRefreshSessionKey(firstLogin.refreshCookie());
            String secondSessionKey = getRefreshSessionKey(secondLogin.refreshCookie());
            String sessionSetKey = getRefreshSessionSetKey(firstLogin.refreshCookie());
            String firstSessionId = jwtTokenProvider.getSessionId(firstLogin.refreshCookie().getValue());
            String secondSessionId = jwtTokenProvider.getSessionId(secondLogin.refreshCookie().getValue());

            assertThat(redisTemplate.opsForSet().members(sessionSetKey))
                    .contains(firstSessionId, secondSessionId);

            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(csrf())
                            .cookie(firstLogin.accessCookie(), firstLogin.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(redisTemplate.opsForValue().get(firstSessionKey)).isNull();
            assertThat(redisTemplate.opsForValue().get(secondSessionKey)).isEqualTo(secondLogin.refreshCookie().getValue());
            assertThat(asStringSet(redisTemplate.opsForSet().members(sessionSetKey)))
                    .doesNotContain(firstSessionId)
                    .contains(secondSessionId);
        }

        @Test
        @DisplayName("웹 로그아웃 - refresh session도 함께 폐기한다")
        void webLogout_Success_RevokesRefreshSession() throws Exception {
            LoginCookies loginCookies = loginAsParent();
            String sessionKey = getRefreshSessionKey(loginCookies.refreshCookie());

            assertThat(redisTemplate.opsForValue().get(sessionKey)).isEqualTo(loginCookies.refreshCookie().getValue());

            mockMvc.perform(post("/logout")
                            .with(csrf())
                            .cookie(loginCookies.accessCookie(), loginCookies.refreshCookie()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));

            assertThat(redisTemplate.opsForValue().get(sessionKey)).isNull();
        }
    }

    @Nested
    @DisplayName("활성 세션 관리 API")
    class SessionManagementApiTest {

        @Test
        @DisplayName("활성 세션 목록 - 현재 세션과 다른 기기 세션을 함께 반환한다")
        void getActiveSessions_Success_ReturnsCurrentAndOtherSessions() throws Exception {
            LoginCookies firstLogin = loginAsParent("198.51.100.10", "JUnit Session Browser/1.0 (Mac OS X)");
            LoginCookies secondLogin = loginAsParent("198.51.100.11", "JUnit Session Browser/2.0 (Windows NT 10.0)");

            String firstSessionId = jwtTokenProvider.getSessionId(firstLogin.refreshCookie().getValue());
            String secondSessionId = jwtTokenProvider.getSessionId(secondLogin.refreshCookie().getValue());

            mockMvc.perform(get("/api/v1/auth/sessions")
                            .cookie(secondLogin.accessCookie(), secondLogin.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].current").value(true))
                    .andExpect(jsonPath("$.data[0].sessionId").value(secondSessionId))
                    .andExpect(jsonPath("$.data[0].signInMethod").value("LOCAL"))
                    .andExpect(jsonPath("$.data[0].signInMethodLabel").value("Local"))
                    .andExpect(jsonPath("$.data[0].deviceLabel").value(org.hamcrest.Matchers.containsString("Browser")))
                    .andExpect(jsonPath("$.data[1].sessionId").value(firstSessionId));

            assertThat(redisTemplate.opsForValue().get(getSessionMetadataKey(secondLogin.refreshCookie()))).isNotNull();
        }

        @Test
        @DisplayName("세션 종료 - 다른 기기 세션을 끊으면 해당 access token도 즉시 무효화된다")
        void revokeSession_Success_ImmediatelyInvalidatesRevokedAccessToken() throws Exception {
            LoginCookies firstLogin = loginAsParent("198.51.100.20", "JUnit Device A");
            LoginCookies secondLogin = loginAsParent("198.51.100.21", "JUnit Device B");

            String firstSessionId = jwtTokenProvider.getSessionId(firstLogin.refreshCookie().getValue());

            mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", firstSessionId)
                            .with(csrf())
                            .cookie(secondLogin.accessCookie(), secondLogin.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/api/v1/auth/me")
                            .cookie(firstLogin.accessCookie(), firstLogin.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/auth/me")
                            .cookie(secondLogin.accessCookie(), secondLogin.refreshCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("세션 종료 - 현재 세션을 종료하면 쿠키를 만료시키고 즉시 인증이 해제된다")
        void revokeCurrentSession_Success_ExpiresCookiesAndInvalidatesCurrentAccessToken() throws Exception {
            LoginCookies login = loginAsParent("198.51.100.22", "JUnit Current Session");
            String currentSessionId = jwtTokenProvider.getSessionId(login.refreshCookie().getValue());

            MvcResult result = mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", currentSessionId)
                            .with(csrf())
                            .cookie(login.accessCookie(), login.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            assertThat(result.getResponse().getCookie("access_token")).isNotNull();
            assertThat(result.getResponse().getCookie("refresh_token")).isNotNull();
            assertThat(result.getResponse().getCookie("access_token").getMaxAge()).isZero();
            assertThat(result.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
            assertThat(redisTemplate.opsForValue().get(getRefreshSessionKey(login.refreshCookie()))).isNull();
            assertThat(redisTemplate.opsForValue().get(getSessionMetadataKey(login.refreshCookie()))).isNull();

            mockMvc.perform(get("/api/v1/auth/me")
                            .cookie(login.accessCookie(), login.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("다른 기기 로그아웃 - 현재 세션만 남기고 나머지 세션을 모두 종료한다")
        void revokeOtherSessions_Success_KeepsOnlyCurrentSession() throws Exception {
            LoginCookies firstLogin = loginAsParent("198.51.100.31", "JUnit Device A");
            LoginCookies secondLogin = loginAsParent("198.51.100.32", "JUnit Device B");

            String currentSessionId = jwtTokenProvider.getSessionId(secondLogin.refreshCookie().getValue());

            mockMvc.perform(delete("/api/v1/auth/sessions/others")
                            .with(csrf())
                            .cookie(secondLogin.accessCookie(), secondLogin.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/api/v1/auth/me")
                            .cookie(firstLogin.accessCookie(), firstLogin.refreshCookie()))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/auth/sessions")
                            .cookie(secondLogin.accessCookie(), secondLogin.refreshCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].current").value(true))
                    .andExpect(jsonPath("$.data[0].sessionId").value(currentSessionId));
        }

        @Test
        @DisplayName("활성 세션 - 인증된 요청이 들어오면 마지막 활동 시각이 갱신된다")
        void authenticatedRequest_TouchesSessionLastSeen() throws Exception {
            LoginCookies login = loginAsParent("198.51.100.41", "JUnit Touch Browser/1.0");

            String refreshKey = getRefreshSessionKey(login.refreshCookie());
            String metadataKey = getSessionMetadataKey(login.refreshCookie());
            Long ttlMs = redisTemplate.getExpire(refreshKey, TimeUnit.MILLISECONDS);
            assertThat(ttlMs).isNotNull();
            assertThat(ttlMs).isPositive();

            AuthSessionRegistryService.SessionMetadata metadata =
                    (AuthSessionRegistryService.SessionMetadata) redisTemplate.opsForValue().get(metadataKey);
            assertThat(metadata).isNotNull();

            long oldLastSeen = metadata.getCreatedAtEpochMs() - 120_000L;
            redisTemplate.opsForValue().set(
                    metadataKey,
                    new AuthSessionRegistryService.SessionMetadata(
                            metadata.getSessionId(),
                            metadata.getSignInMethod(),
                            metadata.getClientIp(),
                            metadata.getUserAgent(),
                            metadata.getCreatedAtEpochMs(),
                            oldLastSeen,
                            metadata.getLastRefreshedAtEpochMs(),
                            metadata.getExpiresAtEpochMs()
                    ),
                    ttlMs,
                    TimeUnit.MILLISECONDS
            );

            mockMvc.perform(get("/api/v1/auth/me")
                            .with(remoteAddr("198.51.100.41"))
                            .with(header("User-Agent", "JUnit Touch Browser/1.1"))
                            .cookie(login.accessCookie(), login.refreshCookie()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            AuthSessionRegistryService.SessionMetadata touched =
                    (AuthSessionRegistryService.SessionMetadata) redisTemplate.opsForValue().get(metadataKey);
            assertThat(touched).isNotNull();
            assertThat(touched.getLastSeenAtEpochMs()).isGreaterThan(oldLastSeen);
        }
    }

    private LoginCookies loginAsParent() throws Exception {
        return loginAsParent("127.0.0.1");
    }

    private LoginCookies loginAsParent(String ipAddress) throws Exception {
        return loginAsParent(ipAddress, "JUnit Test Browser/1.0");
    }

    private LoginCookies loginAsParent(String ipAddress, String userAgent) throws Exception {
        String loginBody = """
                {
                    "email": "parent@test.com",
                    "password": "test1234"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .with(remoteAddr(ipAddress))
                        .with(header("User-Agent", userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        return new LoginCookies(
                result.getResponse().getCookie("access_token"),
                result.getResponse().getCookie("refresh_token")
        );
    }

    private String getRefreshSessionKey(Cookie refreshCookie) {
        return "refresh:session:%d:%s".formatted(
                jwtTokenProvider.getMemberId(refreshCookie.getValue()),
                jwtTokenProvider.getSessionId(refreshCookie.getValue())
        );
    }

    private String getRefreshSessionSetKey(Cookie refreshCookie) {
        return "refresh:sessions:%d".formatted(jwtTokenProvider.getMemberId(refreshCookie.getValue()));
    }

    private String getSessionMetadataKey(Cookie refreshCookie) {
        return "refresh:session:meta:%d:%s".formatted(
                jwtTokenProvider.getMemberId(refreshCookie.getValue()),
                jwtTokenProvider.getSessionId(refreshCookie.getValue())
        );
    }

    private Set<String> asStringSet(Set<Object> values) {
        if (values == null) {
            return Set.of();
        }

        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }

    private record LoginCookies(Cookie accessCookie, Cookie refreshCookie) {
    }

    private RequestPostProcessor remoteAddr(String ipAddress) {
        return request -> {
            request.setRemoteAddr(ipAddress);
            return request;
        };
    }

    private RequestPostProcessor header(String name, String value) {
        return request -> {
            request.addHeader(name, value);
            return request;
        };
    }
}
