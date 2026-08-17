package com.kinderp.domain.auth.controller;

import com.kinderp.global.security.AuthenticatedMemberResolver;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

/**
 * 인증 뷰 컨트롤러
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthViewController {

    private final AuthenticatedMemberResolver authenticatedMemberResolver;

    /**
     * 로그인 페이지
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            Model model) {
        resolveLoginError(error).ifPresent(loginError -> {
            model.addAttribute("loginErrorTitle", loginError.title());
            model.addAttribute("loginErrorMessage", loginError.message());
            model.addAttribute("loginErrorHint", loginError.hint());
        });
        return "auth/login";
    }

    /**
     * 회원가입 페이지
     */
    @GetMapping("/signup")
    public String signUpPage() {
        return "auth/signup";
    }

    /**
     * 메인 페이지 (임시)
     * 인증되지 않은 사용자도 접근 가능 (랜딩 페이지)
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * 프로필 페이지
     */
    @GetMapping("/profile")
    public String profilePage(Authentication authentication) {
        if (authenticatedMemberResolver.resolve(authentication).isEmpty()) {
            return "redirect:/login";
        }
        return "auth/profile";
    }

    /**
     * 설정 페이지
     */
    @GetMapping("/settings")
    public String settingsPage(Authentication authentication,
                               @RequestParam(value = "socialLinkStatus", required = false) String socialLinkStatus,
                               @RequestParam(value = "provider", required = false) String provider,
                               @RequestParam(value = "reason", required = false) String reason,
                               Model model) {
        Member member = authenticatedMemberResolver.resolve(authentication).orElse(null);
        if (member == null) {
            return "redirect:/login";
        }

        model.addAttribute("passwordChangeAvailable", member.hasLocalPassword());
        model.addAttribute("linkedSocialProviderSummary", member.getLinkedSocialProviderSummary());
        model.addAttribute("googleLinked", member.isLinkedTo(MemberAuthProvider.GOOGLE));
        model.addAttribute("kakaoLinked", member.isLinkedTo(MemberAuthProvider.KAKAO));
        model.addAttribute("googleReconnectOnly", member.hasSocialAccountHistory(MemberAuthProvider.GOOGLE)
                && !member.isLinkedTo(MemberAuthProvider.GOOGLE));
        model.addAttribute("kakaoReconnectOnly", member.hasSocialAccountHistory(MemberAuthProvider.KAKAO)
                && !member.isLinkedTo(MemberAuthProvider.KAKAO));
        model.addAttribute("googleUnlinkAllowed", member.canUnlinkSocialAccount(MemberAuthProvider.GOOGLE));
        model.addAttribute("kakaoUnlinkAllowed", member.canUnlinkSocialAccount(MemberAuthProvider.KAKAO));
        model.addAttribute("googleUnlinkBlockedReason", resolveSocialUnlinkBlockedReason(member, MemberAuthProvider.GOOGLE));
        model.addAttribute("kakaoUnlinkBlockedReason", resolveSocialUnlinkBlockedReason(member, MemberAuthProvider.KAKAO));
        resolveSocialLinkFeedback(socialLinkStatus, provider, reason).ifPresent(feedback -> {
            model.addAttribute("socialLinkAlertTone", feedback.tone());
            model.addAttribute("socialLinkAlertTitle", feedback.title());
            model.addAttribute("socialLinkAlertMessage", feedback.message());
        });
        return "auth/settings";
    }

    private Optional<LoginErrorContent> resolveLoginError(String error) {
        if (error == null || error.isBlank()) {
            return Optional.empty();
        }

        return switch (error) {
            case "social_account_conflict" -> Optional.of(new LoginErrorContent(
                    "이미 가입된 계정이 있습니다",
                    "같은 이메일의 기존 계정이 있어 소셜 계정을 자동으로 연결하지 않았습니다.",
                    "기존 로그인 방식으로 로그인해 주세요. 자동 연결은 보안상 허용하지 않습니다."
            ));
            case "social_login_failed" -> Optional.of(new LoginErrorContent(
                    "소셜 로그인에 실패했습니다",
                    "소셜 로그인 처리 중 문제가 발생했습니다.",
                    "잠시 후 다시 시도해 주세요."
            ));
            default -> Optional.empty();
        };
    }

    private record LoginErrorContent(String title, String message, String hint) {
    }

    private Optional<SocialLinkFeedback> resolveSocialLinkFeedback(String status, String provider, String reason) {
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }

        String providerLabel = resolveProviderLabel(provider);

        return switch (status) {
            case "success" -> Optional.of(resolveSocialLinkSuccess(reason, providerLabel));
            case "info" -> Optional.of(new SocialLinkFeedback(
                    "info",
                    "이미 연결된 계정입니다",
                    providerLabel + " 계정은 이미 현재 계정에 연결되어 있습니다."
            ));
            case "error" -> Optional.of(resolveSocialLinkError(reason, providerLabel));
            default -> Optional.empty();
        };
    }

    private SocialLinkFeedback resolveSocialLinkSuccess(String reason, String providerLabel) {
        if ("unlinked".equals(reason)) {
            return new SocialLinkFeedback(
                    "success",
                    providerLabel + " 연결을 해제했습니다",
                    "이제 해당 소셜 로그인은 사용할 수 없습니다. 남아 있는 로그인 수단으로 계속 이용해 주세요."
            );
        }

        return new SocialLinkFeedback(
                "success",
                providerLabel + " 계정을 연결했습니다",
                "다음 로그인부터 이메일/비밀번호 또는 " + providerLabel + " 로그인 중 원하는 방식을 사용할 수 있습니다."
        );
    }

    private SocialLinkFeedback resolveSocialLinkError(String reason, String providerLabel) {
        return switch (reason) {
            case "provider-in-use" -> new SocialLinkFeedback(
                    "error",
                    providerLabel + " 계정을 연결할 수 없습니다",
                    "해당 소셜 계정은 이미 다른 계정에 연결되어 있습니다."
            );
            case "provider-slot-occupied" -> new SocialLinkFeedback(
                    "error",
                    providerLabel + " 계정을 연결할 수 없습니다",
                    "현재 계정에는 이미 다른 " + providerLabel + " 계정이 연결되어 있습니다. 먼저 기존 연결을 해제해 주세요."
            );
            case "provider-replacement-not-allowed" -> new SocialLinkFeedback(
                    "error",
                    providerLabel + " 계정을 교체할 수 없습니다",
                    "처음 연결했던 동일한 " + providerLabel + " 계정만 다시 연결할 수 있습니다."
            );
            case "provider-mismatch" -> new SocialLinkFeedback(
                    "error",
                    "잘못된 소셜 로그인 응답입니다",
                    "설정 화면에서 선택한 제공자와 다른 응답이 돌아와 연결을 중단했습니다."
            );
            case "unsupported" -> new SocialLinkFeedback(
                    "error",
                    "지원하지 않는 소셜 로그인입니다",
                    "현재는 Google과 Kakao만 연결할 수 있습니다."
            );
            case "unlink-requires-local-password" -> new SocialLinkFeedback(
                    "error",
                    "소셜 연결을 해제할 수 없습니다",
                    "계정 잠금 방지를 위해 다른 로그인 수단을 먼저 확보해야 합니다."
            );
            case "not-linked" -> new SocialLinkFeedback(
                    "error",
                    "연결된 소셜 계정을 찾을 수 없습니다",
                    "이미 해제되었거나 다른 제공자가 연결된 상태입니다."
            );
            default -> new SocialLinkFeedback(
                    "error",
                    "소셜 계정 연결에 실패했습니다",
                    "잠시 후 다시 시도해 주세요."
            );
        };
    }

    private String resolveSocialUnlinkBlockedReason(Member member, MemberAuthProvider provider) {
        if (!member.isLinkedTo(provider) || member.canUnlinkSocialAccount(provider)) {
            return null;
        }
        return "다른 로그인 수단을 먼저 확보해야 연결을 해제할 수 있습니다.";
    }

    private String resolveProviderLabel(String provider) {
        if ("google".equalsIgnoreCase(provider)) {
            return "Google";
        }
        if ("kakao".equalsIgnoreCase(provider)) {
            return "Kakao";
        }
        return "소셜";
    }

    private record SocialLinkFeedback(String tone, String title, String message) {
    }
}
