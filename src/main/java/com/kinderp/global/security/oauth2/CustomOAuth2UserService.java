package com.kinderp.global.security.oauth2;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.from(registrationId, oAuth2User.getAttributes());
        if (userInfo.getProviderId() == null || userInfo.getProviderId().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_provider_id"),
                    "소셜 사용자 식별자를 가져올 수 없습니다"
            );
        }
        return oAuth2User;
    }
}
