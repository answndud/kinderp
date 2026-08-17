package com.kinderp.global.security.oauth2;

import com.kinderp.domain.member.entity.MemberAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LinkSessionService {

    private static final String LINK_MEMBER_ID_SESSION_KEY = "oauth2_link_member_id";
    private static final String LINK_PROVIDER_SESSION_KEY = "oauth2_link_provider";

    public void store(HttpServletRequest request, Long memberId, MemberAuthProvider provider) {
        HttpSession session = request.getSession(true);
        session.setAttribute(LINK_MEMBER_ID_SESSION_KEY, memberId);
        session.setAttribute(LINK_PROVIDER_SESSION_KEY, provider.name());
    }

    public Optional<SocialLinkIntent> load(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        Object memberIdAttr = session.getAttribute(LINK_MEMBER_ID_SESSION_KEY);
        Object providerAttr = session.getAttribute(LINK_PROVIDER_SESSION_KEY);
        if (!(memberIdAttr instanceof Long memberId) || !(providerAttr instanceof String providerName)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new SocialLinkIntent(memberId, MemberAuthProvider.valueOf(providerName)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record SocialLinkIntent(Long memberId, MemberAuthProvider provider) {
    }
}
