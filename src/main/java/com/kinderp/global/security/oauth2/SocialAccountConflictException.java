package com.kinderp.global.security.oauth2;

import com.kinderp.domain.member.entity.MemberAuthProvider;

public class SocialAccountConflictException extends RuntimeException {

    private final MemberAuthProvider attemptedProvider;

    public SocialAccountConflictException(MemberAuthProvider attemptedProvider) {
        super("Social account conflict");
        this.attemptedProvider = attemptedProvider;
    }

    public MemberAuthProvider getAttemptedProvider() {
        return attemptedProvider;
    }
}
