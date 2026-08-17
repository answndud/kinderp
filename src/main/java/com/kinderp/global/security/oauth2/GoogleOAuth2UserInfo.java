package com.kinderp.global.security.oauth2;

import com.kinderp.domain.member.entity.MemberAuthProvider;

import java.util.Map;

public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public MemberAuthProvider getProvider() {
        return MemberAuthProvider.GOOGLE;
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        String name = (String) attributes.get("name");
        return name == null || name.isBlank() ? "Google 사용자" : name;
    }
}
