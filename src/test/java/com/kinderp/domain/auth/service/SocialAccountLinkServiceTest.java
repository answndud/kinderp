package com.kinderp.domain.auth.service;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("소셜 계정 연결 서비스 테스트")
@Tag("integration")
public class SocialAccountLinkServiceTest extends BaseIntegrationTest {

    @Autowired
    private SocialAccountLinkService socialAccountLinkService;

    @Test
    @DisplayName("같은 provider는 동일한 providerId로만 재연결할 수 있다")
    void linkSocialAccount_Success_WhenRelinkingSameProviderIdentity() {
        Member member = Member.createSocial(
                "immutable-provider@test.com",
                "불변소셜회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-immutable-123"
        );
        member.changePassword(passwordEncoder.encode("Local1234!"));
        member.assignKindergarten(kindergarten);
        memberRepository.saveAndFlush(member);

        socialAccountLinkService.unlinkSocialAccount(member.getId(), MemberAuthProvider.GOOGLE);
        socialAccountLinkService.linkSocialAccount(member.getId(), MemberAuthProvider.GOOGLE, "google-immutable-123");

        Member updatedMember = memberRepository.findByIdWithSocialAccounts(member.getId()).orElseThrow();
        assertThat(updatedMember.isLinkedTo(MemberAuthProvider.GOOGLE)).isTrue();
        assertThat(updatedMember.getAuthProvider()).isEqualTo(MemberAuthProvider.GOOGLE);
        assertThat(updatedMember.getProviderId()).isEqualTo("google-immutable-123");
    }

    @Test
    @DisplayName("같은 provider를 다른 providerId로 교체하려 하면 차단한다")
    void linkSocialAccount_Fail_WhenReplacingSameProviderIdentity() {
        Member member = Member.createSocial(
                "immutable-provider-blocked@test.com",
                "불변소셜차단회원",
                MemberRole.PARENT,
                MemberAuthProvider.GOOGLE,
                "google-original-123"
        );
        member.changePassword(passwordEncoder.encode("Local1234!"));
        member.assignKindergarten(kindergarten);
        memberRepository.saveAndFlush(member);

        socialAccountLinkService.unlinkSocialAccount(member.getId(), MemberAuthProvider.GOOGLE);

        assertThatThrownBy(() -> socialAccountLinkService.linkSocialAccount(
                member.getId(),
                MemberAuthProvider.GOOGLE,
                "google-replacement-456"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_PROVIDER_REPLACEMENT_NOT_ALLOWED);

        Member updatedMember = memberRepository.findByIdWithSocialAccounts(member.getId()).orElseThrow();
        assertThat(updatedMember.isLinkedTo(MemberAuthProvider.GOOGLE)).isFalse();
        assertThat(updatedMember.hasSocialAccountHistory(MemberAuthProvider.GOOGLE)).isTrue();
    }
}
