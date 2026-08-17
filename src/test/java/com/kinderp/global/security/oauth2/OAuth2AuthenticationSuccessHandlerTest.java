package com.kinderp.global.security.oauth2;

import com.kinderp.domain.auth.service.AuthService;
import com.kinderp.domain.auth.service.SocialAccountLinkService;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.ClientIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("fast")
public class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuthService authService;

    @Mock
    private SocialAccountLinkService socialAccountLinkService;

    @Mock
    private OAuth2LinkSessionService oauth2LinkSessionService;

    @Mock
    private AuthAuditLogService authAuditLogService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OAuth2 로그인: 기존 이메일과 충돌하면 자동 연결하지 않고 임시 세션을 정리한 뒤 로그인 페이지로 돌려보낸다")
    void onAuthenticationSuccess_RedirectsToConflict_WhenEmailAlreadyExists() throws Exception {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_PARENT")),
                Map.of(
                        "sub", "google-sub-123",
                        "email", "existing@test.com",
                        "name", "기존회원"
                ),
                "sub"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );

        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(memberRepository.findBySocialProviderAndProviderId(MemberAuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("existing@test.com"))
                .thenReturn(true);
        when(oauth2LinkSessionService.load(request)).thenReturn(Optional.empty());

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=social_account_conflict");
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authAuditLogService).recordLoginFailure(
                "existing@test.com",
                MemberAuthProvider.GOOGLE,
                "203.0.113.10",
                "social_account_conflict"
        );
        verify(authService, never()).loginBySocial(any(), any(MemberAuthProvider.class), any(), any(), any());
    }

    @Test
    @DisplayName("OAuth2 로그인: link intent가 있으면 신규 가입 대신 현재 계정에 provider를 연결한다")
    void onAuthenticationSuccess_LinksProviderToCurrentMember_WhenLinkIntentExists() throws Exception {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_PARENT")),
                Map.of(
                        "sub", "google-sub-123",
                        "email", "linked@test.com",
                        "name", "연결회원"
                ),
                "sub"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );

        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(oauth2LinkSessionService.load(request))
                .thenReturn(Optional.of(new OAuth2LinkSessionService.SocialLinkIntent(1L, MemberAuthProvider.GOOGLE)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                Member.create("linked@test.com", "encoded", "연결회원", "01000000000", MemberRole.PARENT)
        ));

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/settings?socialLinkStatus=success&provider=google");
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authAuditLogService).recordSocialLinkSuccess(
                1L,
                "linked@test.com",
                MemberAuthProvider.GOOGLE,
                "203.0.113.10"
        );
        verify(socialAccountLinkService).linkSocialAccount(1L, MemberAuthProvider.GOOGLE, "google-sub-123");
        verify(authService, never()).loginBySocial(any(), any(MemberAuthProvider.class), any(), any(), any());
    }

    @Test
    @DisplayName("OAuth2 연결: 같은 provider의 다른 계정으로 교체하려 하면 구체적인 settings 오류로 돌려보낸다")
    void onAuthenticationSuccess_RedirectsToReplacementBlocked_WhenProviderIdentityChanges() throws Exception {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_PARENT")),
                Map.of(
                        "sub", "google-replacement-456",
                        "email", "linked@test.com",
                        "name", "연결회원"
                ),
                "sub"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );

        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(oauth2LinkSessionService.load(request))
                .thenReturn(Optional.of(new OAuth2LinkSessionService.SocialLinkIntent(1L, MemberAuthProvider.GOOGLE)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                Member.create("linked@test.com", "encoded", "연결회원", "01000000000", MemberRole.PARENT)
        ));
        doThrow(new BusinessException(ErrorCode.SOCIAL_PROVIDER_REPLACEMENT_NOT_ALLOWED))
                .when(socialAccountLinkService)
                .linkSocialAccount(1L, MemberAuthProvider.GOOGLE, "google-replacement-456");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/settings?socialLinkStatus=error&reason=provider-replacement-not-allowed"
        );
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authAuditLogService).recordSocialLinkFailure(
                1L,
                "linked@test.com",
                MemberAuthProvider.GOOGLE,
                "203.0.113.10",
                "A011"
        );
        verify(authService, never()).loginBySocial(any(), any(MemberAuthProvider.class), any(), any(), any());
    }
}
