package com.kinderp.global.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo from(String registrationId, Map<String, Object> attributes) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return new GoogleOAuth2UserInfo(attributes);
        }
        if ("kakao".equalsIgnoreCase(registrationId)) {
            return new KakaoOAuth2UserInfo(attributes);
        }

        throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider"),
                "지원하지 않는 OAuth2 provider 입니다: " + registrationId
        );
    }
}
