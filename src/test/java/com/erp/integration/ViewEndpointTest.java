package com.erp.integration;

import com.erp.common.TestcontainersSupport;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.kindergarten.repository.KindergartenRepository;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberAuthProvider;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.member.repository.MemberRepository;
import com.erp.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 뷰 엔드포인트 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class ViewEndpointTest extends TestcontainersSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private KindergartenRepository kindergartenRepository;

    @Test
    void testHomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void testLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
                .andDo(result -> assertThat(result.getResponse().getCookie("XSRF-TOKEN"))
                        .extracting(cookie -> cookie.getAttribute("SameSite"))
                        .isEqualTo("Strict"));
    }

    @Test
    void testLoginPageWithSocialAccountConflictError() throws Exception {
        mockMvc.perform(get("/login").param("error", "social_account_conflict"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 가입된 계정이 있습니다")))
                .andExpect(content().string(containsString("소셜 계정을 자동으로 연결하지 않았습니다.")))
                .andExpect(content().string(containsString("기존 로그인 방식으로 로그인해 주세요.")));
    }

    @Test
    void testSignupPage() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk());
    }

    // 인증이 필요한 페이지들 (로그인 없으면 리다이렉트되어야 함)
    @Test
    void testNotepadPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/notepad"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testAttendancePageWithoutAuth() throws Exception {
        mockMvc.perform(get("/attendance"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testAttendanceRequestsPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/attendance-requests"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testAnnouncementsPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/announcements"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testProfilePageWithoutAuth() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testSettingsPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testAuditLogsPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testDomainAuditLogsPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/domain-audit-logs"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testNotificationOutboxPageWithoutAuth() throws Exception {
        mockMvc.perform(get("/notification-outbox"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testSocialLinkStartRedirectsToOauthAuthorization() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("연결 유치원", "서울시", "010-2222-3333", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member localMember = Member.create(
                "link-local@test.com",
                "encoded-password",
                "로컬회원",
                "01033334444",
                MemberRole.PARENT
        );
        localMember.assignKindergarten(kindergarten);
        memberRepository.save(localMember);

        mockMvc.perform(get("/auth/social/link/google").with(user(new CustomUserDetails(localMember))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"))
                .andExpect(request().sessionAttribute("oauth2_link_member_id", localMember.getId()))
                .andExpect(request().sessionAttribute("oauth2_link_provider", "GOOGLE"));
    }

    @Test
    void testSettingsPageForSocialOnlyAccountShowsBootstrapPasswordForm() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("소셜 설정 유치원", "서울시", "010-4444-5555", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member socialMember = Member.createSocial(
                "settings-social@test.com",
                "소셜설정회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "settings-google-123"
        );
        socialMember.assignKindergarten(kindergarten);
        memberRepository.save(socialMember);

        mockMvc.perform(get("/settings").with(user(new CustomUserDetails(socialMember))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("연결됨: Google")))
                .andExpect(content().string(containsString("소셜 로그인 전용")))
                .andExpect(content().string(containsString("로컬 비밀번호 설정")))
                .andExpect(content().string(containsString("다른 로그인 수단을 먼저 확보해야 연결을 해제할 수 있습니다.")))
                .andExpect(content().string(not(containsString("현재 비밀번호"))));
    }

    @Test
    void testSettingsPageForPrincipalShowsAuditLogLink() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("원장 설정 유치원", "서울시", "010-5555-6666", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member principal = Member.create(
                "principal-settings@test.com",
                "encoded-password",
                "설정원장",
                "01022223333",
                MemberRole.PRINCIPAL
        );
        principal.assignKindergarten(kindergarten);
        memberRepository.save(principal);

        mockMvc.perform(get("/settings").with(user(new CustomUserDetails(principal))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("운영 도구")))
                .andExpect(content().string(containsString("인증 감사 로그 열기")))
                .andExpect(content().string(containsString("업무 감사 로그 열기")))
                .andExpect(content().string(containsString("활성 세션")))
                .andExpect(content().string(containsString("다른 기기 로그아웃")));
    }

    @Test
    void testSettingsPageWithLocalPasswordAndLinkedSocialAccountShowsUnlinkButton() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("연결해제 유치원", "서울시", "010-7777-8888", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member linkedMember = Member.createSocial(
                "unlink-view@test.com",
                "연결해제화면회원",
                MemberRole.PARENT,
                MemberAuthProvider.KAKAO,
                "kakao-view-123"
        );
        linkedMember.changePassword("encoded-local-password");
        linkedMember.assignKindergarten(kindergarten);
        memberRepository.save(linkedMember);

        mockMvc.perform(get("/settings").with(user(new CustomUserDetails(linkedMember))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("연결됨: Kakao")))
                .andExpect(content().string(containsString("연결 해제")))
                .andExpect(content().string(containsString("다른 로그인 수단이 남아 있어 연결 해제를 허용합니다.")));
    }

    @Test
    void testSettingsPageShowsReconnectGuidanceForHistoricallyLinkedProvider() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("재연결 정책 유치원", "서울시", "010-1212-3434", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member relinkMember = Member.createSocial(
                "relink-policy@test.com",
                "재연결정책회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-relink-123"
        );
        relinkMember.changePassword("encoded-local-password");
        relinkMember.assignKindergarten(kindergarten);
        relinkMember.unlinkSocialAccount(MemberAuthProvider.GOOGLE);
        memberRepository.save(relinkMember);

        mockMvc.perform(get("/settings").with(user(new CustomUserDetails(relinkMember))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Google 재연결")))
                .andExpect(content().string(containsString("처음 연결했던 동일한 Google 계정만 다시 연결할 수 있습니다.")))
                .andExpect(content().string(not(containsString("Google 연결됨"))));
    }

    @Test
    void testSettingsPageWithMultipleLinkedSocialAccountsShowsBothProviders() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("다중 연결 유치원", "서울시", "010-9999-1111", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member multiLinkedMember = Member.createSocial(
                "multi-linked@test.com",
                "다중연결회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-multi-view-123"
        );
        multiLinkedMember.linkSocialAccount(MemberAuthProvider.KAKAO, "kakao-multi-view-456");
        multiLinkedMember.assignKindergarten(kindergarten);
        memberRepository.save(multiLinkedMember);

        mockMvc.perform(get("/settings").with(user(new CustomUserDetails(multiLinkedMember))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("연결됨: Google, Kakao")))
                .andExpect(content().string(containsString("Google 연결됨")))
                .andExpect(content().string(containsString("Kakao 연결됨")))
                .andExpect(content().string(containsString("다른 로그인 수단이 남아 있어 연결 해제를 허용합니다.")));
    }

    @Test
    void testAuditLogsPageForPrincipal() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("감사 로그 유치원", "서울시", "010-8989-7878", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member principal = Member.create(
                "audit-principal@test.com",
                "encoded-password",
                "감사원장",
                "01011112222",
                MemberRole.PRINCIPAL
        );
        principal.assignKindergarten(kindergarten);
        memberRepository.save(principal);

        mockMvc.perform(get("/audit-logs").with(user(new CustomUserDetails(principal))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("인증 감사 로그")))
                .andExpect(content().string(containsString("운영 환경에서는 API 계약 문서를 비공개로 유지합니다")))
                .andExpect(content().string(containsString("로그인, refresh, 소셜 연결/해제 이벤트")));
    }

    @Test
    void testAuditLogsPageForTeacherForbidden() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("감사 로그 권한 유치원", "서울시", "010-6767-5656", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member teacher = Member.create(
                "audit-teacher@test.com",
                "encoded-password",
                "감사교사",
                "01033334444",
                MemberRole.TEACHER
        );
        teacher.assignKindergarten(kindergarten);
        memberRepository.save(teacher);

        mockMvc.perform(get("/audit-logs").with(user(new CustomUserDetails(teacher))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDomainAuditLogsPageForPrincipal() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("업무 감사 유치원", "서울시", "010-9898-7878", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member principal = Member.create(
                "domain-audit-principal@test.com",
                "encoded-password",
                "업무감사원장",
                "01011113333",
                MemberRole.PRINCIPAL
        );
        principal.assignKindergarten(kindergarten);
        memberRepository.save(principal);

        mockMvc.perform(get("/domain-audit-logs").with(user(new CustomUserDetails(principal))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("업무 감사 로그")))
                .andExpect(content().string(containsString("비즈니스 상태 변경 이력")))
                .andExpect(content().string(containsString("입학, 출결 요청, 공지 수정/삭제")));
    }

    @Test
    void testNotificationOutboxPageForPrincipal() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("알림 운영 유치원", "서울시", "010-9898-1111", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member principal = Member.create(
                "outbox-view-principal@test.com",
                "encoded-password",
                "알림운영원장",
                "01011114444",
                MemberRole.PRINCIPAL
        );
        principal.assignKindergarten(kindergarten);
        memberRepository.save(principal);

        mockMvc.perform(get("/notification-outbox").with(user(new CustomUserDetails(principal))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("알림 전달 큐(Outbox) 운영")))
                .andExpect(content().string(containsString("Dead-letter 채널")))
                .andExpect(content().string(containsString("/api/v1/notification-outbox/summary")));
    }

    @Test
    void testNotificationOutboxPageForTeacherForbidden() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("알림 운영 권한 유치원", "서울시", "010-9898-2222", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member teacher = Member.create(
                "outbox-view-teacher@test.com",
                "encoded-password",
                "알림운영교사",
                "01011115555",
                MemberRole.TEACHER
        );
        teacher.assignKindergarten(kindergarten);
        memberRepository.save(teacher);

        mockMvc.perform(get("/notification-outbox").with(user(new CustomUserDetails(teacher))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAttendanceRequestsPageForParent() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("출결 요청 유치원", "서울시", "010-2121-3434", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member parent = Member.create(
                "attendance-parent@test.com",
                "encoded-password",
                "출결학부모",
                "01088889999",
                MemberRole.PARENT
        );
        parent.assignKindergarten(kindergarten);
        memberRepository.save(parent);

        mockMvc.perform(get("/attendance-requests").with(user(new CustomUserDetails(parent))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내 출결 변경 요청")))
                .andExpect(content().string(containsString("출결 변경 요청 보내기")))
                .andExpect(content().string(containsString("학부모는 출석 record를 직접 수정하지 않고 변경 요청을 제출합니다.")));
    }

    @Test
    void testAttendanceRequestsPageForTeacher() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("출결 검토 유치원", "서울시", "010-4545-6767", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member teacher = Member.create(
                "attendance-teacher@test.com",
                "encoded-password",
                "출결교사",
                "01056565656",
                MemberRole.TEACHER
        );
        teacher.assignKindergarten(kindergarten);
        memberRepository.save(teacher);

        mockMvc.perform(get("/attendance-requests").with(user(new CustomUserDetails(teacher))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("출결 변경 요청 검토")))
                .andExpect(content().string(containsString("승인 대기 요청")))
                .andExpect(content().string(containsString("교사 검토 큐")));
    }

    @Test
    void testProfilePageWithOAuth2Principal() throws Exception {
        Kindergarten kindergarten = kindergartenRepository.save(
                Kindergarten.create("소셜 유치원", "서울시", "010-1111-2222", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        Member socialMember = Member.createSocial(
                "social-parent@test.com",
                "소셜학부모",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-sub-123"
        );
        socialMember.assignKindergarten(kindergarten);
        memberRepository.save(socialMember);

        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_PARENT")),
                Map.of(
                        "sub", "google-sub-123",
                        "email", "social-parent@test.com",
                        "name", "소셜학부모"
                ),
                "sub"
        );

        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );

        mockMvc.perform(get("/profile").with(authentication(authentication)))
                .andExpect(status().isOk());
    }
}
