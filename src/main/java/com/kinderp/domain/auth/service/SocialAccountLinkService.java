package com.kinderp.domain.auth.service;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountLinkService {

    private final MemberRepository memberRepository;

    @Transactional
    public void linkSocialAccount(Long memberId, MemberAuthProvider provider, String providerId) {
        if (provider == null || provider == MemberAuthProvider.LOCAL || providerId == null || providerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Member member = memberRepository.findByIdWithSocialAccounts(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        memberRepository.findByAnySocialProviderAndProviderId(provider, providerId)
                .filter(existing -> !existing.getId().equals(memberId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
                });

        if (member.hasProviderBindingWithDifferentIdentity(provider, providerId)) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_REPLACEMENT_NOT_ALLOWED);
        }

        member.linkSocialAccount(provider, providerId);
    }

    @Transactional
    public void unlinkSocialAccount(Long memberId, MemberAuthProvider provider) {
        if (provider == null || provider == MemberAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Member member = memberRepository.findByIdWithSocialAccounts(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!member.isLinkedTo(provider)) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        }

        if (!member.canUnlinkSocialAccount(provider)) {
            throw new BusinessException(ErrorCode.SOCIAL_UNLINK_REQUIRES_LOCAL_PASSWORD);
        }

        member.unlinkSocialAccount(provider);
    }
}
