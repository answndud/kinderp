package com.kinderp.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ContentSecurityPolicyFilterTest {

    private final ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter();

    @Test
    void addsNonceBasedEnforcedPolicyAndRequestAttribute() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String policy = response.getHeader("Content-Security-Policy");
        String nonce = (String) request.getAttribute(ContentSecurityPolicyFilter.NONCE_ATTRIBUTE);
        assertThat(nonce).isNotBlank();
        assertThat(policy).contains("'nonce-" + nonce + "'");
        assertThat(policy).doesNotContain("'unsafe-eval'");
        assertThat(policy).contains("script-src-attr 'none'");
        assertThat(policy).doesNotContain("cdn.tailwindcss.com", "unpkg.com", "cdn.jsdelivr.net", "fonts.googleapis.com");
        assertThat(response.getHeader("Content-Security-Policy-Report-Only")).isNull();
    }
}
