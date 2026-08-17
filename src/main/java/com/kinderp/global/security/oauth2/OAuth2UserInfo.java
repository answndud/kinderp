package com.kinderp.global.security.oauth2;

import com.kinderp.domain.member.entity.MemberAuthProvider;

public interface OAuth2UserInfo {
    MemberAuthProvider getProvider();

    String getProviderId();

    String getEmail();

    String getName();
}
