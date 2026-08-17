package com.kinderp.global.security.oauth2;

import com.kinderp.domain.auth.service.AuthService;
import com.kinderp.domain.auth.service.SocialAccountLinkService;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.ClientIpResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberRepository memberRepository;
    private final SocialAccountLinkService socialAccountLinkService;
    private final AuthService authService;
    private final OAuth2LinkSessionService oauth2LinkSessionService;
    private final AuthAuditLogService authAuditLogService;
    private final ClientIpResolver clientIpResolver;

    public OAuth2AuthenticationSuccessHandler(MemberRepository memberRepository,
                                              SocialAccountLinkService socialAccountLinkService,
                                              @Lazy AuthService authService,
                                              OAuth2LinkSessionService oauth2LinkSessionService,
                                              AuthAuditLogService authAuditLogService,
                                              ClientIpResolver clientIpResolver) {
        this.memberRepository = memberRepository;
        this.socialAccountLinkService = socialAccountLinkService;
        this.authService = authService;
        this.oauth2LinkSessionService = oauth2LinkSessionService;
        this.authAuditLogService = authAuditLogService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2UserInfo userInfo = null;
        String clientIp = clientIpResolver.resolve(request);

        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
                clearTemporaryOAuthSession(request);
                response.sendRedirect("/login?error=social_login_failed");
                return;
            }

            OAuth2User oAuth2User = (OAuth2User) oauthToken.getPrincipal();
            userInfo = OAuth2UserInfoFactory.from(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oAuth2User.getAttributes()
            );

            if (handleSocialLink(request, response, userInfo, clientIp)) {
                return;
            }

            Member member = memberRepository
                    .findBySocialProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
                    .orElse(null);
            if (member == null) {
                member = registerSocialMember(userInfo);
            }

            authService.loginBySocial(member, userInfo.getProvider(), clientIp, request.getHeader("User-Agent"), response);
            authAuditLogService.recordLoginSuccess(
                    member.getId(),
                    member.getEmail(),
                    userInfo.getProvider(),
                    clientIp
            );
            clearTemporaryOAuthSession(request);
            response.sendRedirect(resolveRedirect(member));
        } catch (SocialAccountConflictException ex) {
            authAuditLogService.recordLoginFailure(
                    resolveAuditEmail(userInfo),
                    ex.getAttemptedProvider(),
                    clientIp,
                    "social_account_conflict"
            );
            clearTemporaryOAuthSession(request);
            log.info("Rejected automatic social account linking for provider={}", ex.getAttemptedProvider());
            response.sendRedirect("/login?error=social_account_conflict");
        } catch (Exception ex) {
            authAuditLogService.recordLoginFailure(
                    resolveAuditEmail(userInfo),
                    userInfo != null ? userInfo.getProvider() : null,
                    clientIp,
                    "social_login_failed"
            );
            clearTemporaryOAuthSession(request);
            log.warn("OAuth2 login failed unexpectedly", ex);
            response.sendRedirect("/login?error=social_login_failed");
        }
    }

    private boolean handleSocialLink(HttpServletRequest request,
                                     HttpServletResponse response,
                                     OAuth2UserInfo userInfo,
                                     String clientIp) throws IOException {
        var linkIntent = oauth2LinkSessionService.load(request);
        if (linkIntent.isEmpty()) {
            return false;
        }

        Long memberId = linkIntent.get().memberId();
        String memberEmail = resolveLinkedMemberEmail(memberId);

        if (linkIntent.get().provider() != userInfo.getProvider()) {
            authAuditLogService.recordSocialLinkFailure(
                    memberId,
                    memberEmail,
                    userInfo.getProvider(),
                    clientIp,
                    "provider-mismatch"
            );
            clearTemporaryOAuthSession(request);
            response.sendRedirect("/settings?socialLinkStatus=error&reason=provider-mismatch");
            return true;
        }

        try {
            socialAccountLinkService.linkSocialAccount(
                    memberId,
                    userInfo.getProvider(),
                    userInfo.getProviderId()
            );
            authAuditLogService.recordSocialLinkSuccess(
                    memberId,
                    memberEmail,
                    userInfo.getProvider(),
                    clientIp
            );
            clearTemporaryOAuthSession(request);
            response.sendRedirect("/settings?socialLinkStatus=success&provider=" + userInfo.getProvider().name().toLowerCase());
        } catch (BusinessException ex) {
            authAuditLogService.recordSocialLinkFailure(
                    memberId,
                    memberEmail,
                    userInfo.getProvider(),
                    clientIp,
                    ex.getErrorCode().getCode()
            );
            clearTemporaryOAuthSession(request);
            response.sendRedirect("/settings?socialLinkStatus=error&reason=" + mapLinkErrorReason(ex.getErrorCode()));
        }
        return true;
    }

    private Member registerSocialMember(OAuth2UserInfo userInfo) {
        String email = resolveEmail(userInfo);

        if (memberRepository.existsByEmail(email)) {
            throw new SocialAccountConflictException(userInfo.getProvider());
        }

        Member member = Member.createSocial(
                email,
                userInfo.getName(),
                MemberRole.PARENT,
                userInfo.getProvider(),
                userInfo.getProviderId()
        );
        member.markPending();
        return memberRepository.save(member);
    }

    private String resolveEmail(OAuth2UserInfo userInfo) {
        if (userInfo.getEmail() != null && !userInfo.getEmail().isBlank()) {
            return userInfo.getEmail().trim().toLowerCase();
        }

        MemberAuthProvider provider = userInfo.getProvider();
        return provider.name().toLowerCase() + "_" + userInfo.getProviderId() + "@social.local";
    }

    private String resolveRedirect(Member member) {
        if (member.getRole() == MemberRole.PRINCIPAL && member.getKindergarten() == null) {
            return "/kindergarten/create";
        }
        if (member.getKindergarten() == null || member.getStatus() == MemberStatus.PENDING) {
            return "/applications/pending";
        }
        return "/";
    }

    private void clearTemporaryOAuthSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private String resolveLinkedMemberEmail(Long memberId) {
        if (memberId == null) {
            return null;
        }

        return memberRepository.findById(memberId)
                .map(Member::getEmail)
                .orElse(null);
    }

    private String resolveAuditEmail(OAuth2UserInfo userInfo) {
        if (userInfo == null) {
            return null;
        }

        String email = userInfo.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String mapLinkErrorReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case SOCIAL_ACCOUNT_ALREADY_LINKED -> "provider-in-use";
            case SOCIAL_PROVIDER_SLOT_OCCUPIED -> "provider-slot-occupied";
            case SOCIAL_PROVIDER_REPLACEMENT_NOT_ALLOWED -> "provider-replacement-not-allowed";
            default -> "failed";
        };
    }
}
