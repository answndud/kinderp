package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.authaudit.repository.AuthAuditLogRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("회원 API 테스트")
@Tag("integration")
class MemberApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    @Test
    @DisplayName("회원 탈퇴 - 모든 refresh 세션을 정리하고 현재 브라우저 쿠키를 만료시킨다")
    void withdraw_Success_RevokesAllRefreshSessions() throws Exception {
        LoginCookies firstLogin = loginAsParent();
        LoginCookies secondLogin = loginAsParent();

        String firstSessionKey = getRefreshSessionKey(firstLogin.refreshCookie());
        String secondSessionKey = getRefreshSessionKey(secondLogin.refreshCookie());
        String sessionSetKey = getRefreshSessionSetKey(firstLogin.refreshCookie());

        MvcResult withdrawResult = mockMvc.perform(delete("/api/v1/members/withdraw")
                        .with(csrf())
                        .cookie(firstLogin.accessCookie(), firstLogin.refreshCookie()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        assertThat(redisTemplate.opsForValue().get(firstSessionKey)).isNull();
        assertThat(redisTemplate.opsForValue().get(secondSessionKey)).isNull();
        assertThat(redisTemplate.hasKey(sessionSetKey)).isFalse();
        assertThat(memberRepository.findById(parentMember.getId()))
                .get()
                .extracting(member -> member.getStatus(), member -> member.getDeletedAt() != null)
                .containsExactly(MemberStatus.INACTIVE, true);
        assertThat(withdrawResult.getResponse().getCookie("access_token")).isNotNull();
        assertThat(withdrawResult.getResponse().getCookie("access_token").getMaxAge()).isZero();
        assertThat(withdrawResult.getResponse().getCookie("refresh_token")).isNotNull();
        assertThat(withdrawResult.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
    }

    @Test
    @DisplayName("소셜 전용 계정 - 초기 로컬 비밀번호를 설정할 수 있다")
    void bootstrapPassword_Success_ForSocialOnlyAccount() throws Exception {
        Member socialMember = Member.createSocial(
                "social-bootstrap@test.com",
                "소셜부트스트랩",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-bootstrap-123"
        );
        socialMember.assignKindergarten(kindergarten);
        memberRepository.saveAndFlush(socialMember);

        String requestBody = """
                {
                    "newPassword": "Boot1234!"
                }
                """;

        mockMvc.perform(post("/api/v1/members/password/bootstrap")
                        .with(authenticated(socialMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Member updatedMember = memberRepository.findById(socialMember.getId()).orElseThrow();
        assertThat(updatedMember.hasLocalPassword()).isTrue();
        assertThat(passwordEncoder.matches("Boot1234!", updatedMember.getPassword())).isTrue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "social-bootstrap@test.com",
                                    "password": "Boot1234!"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("로컬 비밀번호가 이미 있는 계정 - 초기 비밀번호 설정 API를 사용할 수 없다")
    void bootstrapPassword_Fail_WhenPasswordAlreadyExists() throws Exception {
        String requestBody = """
                {
                    "newPassword": "Boot1234!"
                }
                """;

        mockMvc.perform(post("/api/v1/members/password/bootstrap")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("M005"));
    }

    @Test
    @DisplayName("로컬 비밀번호가 있는 계정 - 연결된 소셜 로그인을 해제할 수 있다")
    void unlinkSocialAccount_Success_WhenLocalPasswordExists() throws Exception {
        Member linkedMember = Member.createSocial(
                "unlinkable@test.com",
                "연결해제회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-unlink-123"
        );
        linkedMember.changePassword(passwordEncoder.encode("Local1234!"));
        linkedMember.assignKindergarten(kindergarten);
        memberRepository.saveAndFlush(linkedMember);

        mockMvc.perform(delete("/api/v1/members/social-link/google")
                        .with(authenticated(linkedMember))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Member updatedMember = memberRepository.findByIdWithSocialAccounts(linkedMember.getId()).orElseThrow();
        assertThat(updatedMember.getAuthProvider()).isEqualTo(MemberAuthProvider.LOCAL);
        assertThat(updatedMember.getProviderId()).isNull();
        assertThat(updatedMember.hasLocalPassword()).isTrue();
        assertThat(updatedMember.hasLinkedSocialAccount()).isFalse();
        assertThat(updatedMember.hasSocialAccountHistory(MemberAuthProvider.GOOGLE)).isTrue();

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
                        AuthAuditEventType.SOCIAL_UNLINK,
                        AuthAuditResult.SUCCESS,
                        MemberAuthProvider.GOOGLE,
                        "unlinkable@test.com"
                );
    }

    @Test
    @DisplayName("다른 소셜 로그인 수단이 남아 있으면 로컬 비밀번호가 없어도 특정 provider를 해제할 수 있다")
    void unlinkSocialAccount_Success_WhenAnotherSocialProviderRemains() throws Exception {
        Member multiLinkedMember = Member.createSocial(
                "unlink-multi@test.com",
                "다중연결회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-multi-123"
        );
        multiLinkedMember.linkSocialAccount(MemberAuthProvider.KAKAO, "kakao-multi-456");
        multiLinkedMember.assignKindergarten(kindergarten);
        memberRepository.saveAndFlush(multiLinkedMember);

        mockMvc.perform(delete("/api/v1/members/social-link/google")
                        .with(authenticated(multiLinkedMember))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Member updatedMember = memberRepository.findByIdWithSocialAccounts(multiLinkedMember.getId()).orElseThrow();
        assertThat(updatedMember.isLinkedTo(MemberAuthProvider.GOOGLE)).isFalse();
        assertThat(updatedMember.isLinkedTo(MemberAuthProvider.KAKAO)).isTrue();
        assertThat(updatedMember.getAuthProvider()).isEqualTo(MemberAuthProvider.KAKAO);
        assertThat(updatedMember.getProviderId()).isEqualTo("kakao-multi-456");
    }

    @Test
    @DisplayName("로컬 비밀번호가 없는 계정 - 소셜 연결 해제가 차단된다")
    void unlinkSocialAccount_Fail_WhenNoLocalPasswordExists() throws Exception {
        Member socialOnlyMember = Member.createSocial(
                "unlink-blocked@test.com",
                "연결해제차단회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-unlink-blocked-123"
        );
        socialOnlyMember.assignKindergarten(kindergarten);
        memberRepository.saveAndFlush(socialOnlyMember);

        mockMvc.perform(delete("/api/v1/members/social-link/google")
                        .with(authenticated(socialOnlyMember))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A010"));

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
                        AuthAuditEventType.SOCIAL_UNLINK,
                        AuthAuditResult.FAILURE,
                        MemberAuthProvider.GOOGLE,
                        "unlink-blocked@test.com",
                        "A010"
                );
    }

    private LoginCookies loginAsParent() throws Exception {
        String loginBody = """
                {
                    "email": "parent@test.com",
                    "password": "test1234"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
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

    private record LoginCookies(Cookie accessCookie, Cookie refreshCookie) {
    }
}
