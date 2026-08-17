package com.kinderp.global.security;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.security.oauth2.OAuth2UserInfo;
import com.kinderp.global.security.oauth2.OAuth2UserInfoFactory;
import com.kinderp.global.security.user.CustomUserDetails;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedMemberResolver {

    private final MemberRepository memberRepository;

    public Optional<Member> resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return loadMemberWithKindergarten(userDetails.getMemberId());
        }

        if (authentication instanceof OAuth2AuthenticationToken oauthToken
                && principal instanceof OAuth2User oAuth2User) {
            return resolveOAuth2Member(oauthToken, oAuth2User);
        }

        if (principal instanceof UserDetails userDetails) {
            return memberRepository.findByEmail(userDetails.getUsername())
                    .flatMap(member -> loadMemberWithKindergarten(member.getId()));
        }

        if (principal instanceof String username && !"anonymousUser".equals(username)) {
            return memberRepository.findByEmail(username)
                    .flatMap(member -> loadMemberWithKindergarten(member.getId()));
        }

        return Optional.empty();
    }

    private Optional<Member> resolveOAuth2Member(
            OAuth2AuthenticationToken oauthToken,
            OAuth2User oAuth2User
    ) {
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.from(
                oauthToken.getAuthorizedClientRegistrationId(),
                oAuth2User.getAttributes()
        );

        String providerId = userInfo.getProviderId();
        if (providerId != null && !providerId.isBlank()) {
            Optional<Member> providerMatch = memberRepository.findBySocialProviderAndProviderId(
                    userInfo.getProvider(),
                    providerId
            );
            if (providerMatch.isPresent()) {
                return providerMatch;
            }
        }

        String email = userInfo.getEmail();
        if (email != null && !email.isBlank()) {
            return memberRepository.findByEmail(email.trim().toLowerCase())
                    .flatMap(member -> loadMemberWithKindergarten(member.getId()));
        }

        return Optional.empty();
    }

    private Optional<Member> loadMemberWithKindergarten(Long memberId) {
        return memberRepository.findByIdWithKindergartenAndSocialAccounts(memberId);
    }
}
