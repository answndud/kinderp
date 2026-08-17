package com.kinderp.global.security.oauth2;

import com.kinderp.domain.member.entity.MemberAuthProvider;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public MemberAuthProvider getProvider() {
        return MemberAuthProvider.KAKAO;
    }

    @Override
    public String getProviderId() {
        Object id = attributes.get("id");
        return id == null ? null : String.valueOf(id);
    }

    @Override
    public String getEmail() {
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (!(kakaoAccountObj instanceof Map<?, ?> kakaoAccount)) {
            return null;
        }

        Object email = kakaoAccount.get("email");
        return email == null ? null : String.valueOf(email);
    }

    @Override
    public String getName() {
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (kakaoAccountObj instanceof Map<?, ?> kakaoAccount) {
            Object profileObj = kakaoAccount.get("profile");
            if (profileObj instanceof Map<?, ?> profile) {
                Object nickname = profile.get("nickname");
                if (nickname != null && !String.valueOf(nickname).isBlank()) {
                    return String.valueOf(nickname);
                }
            }
        }
        return "Kakao 사용자";
    }
}
