package com.erp.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    public static final String NONCE_ATTRIBUTE = "cspNonce";
    private static final String NONCE_SOURCE_PREFIX = "'nonce-";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String nonce = createNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        response.setHeader("Content-Security-Policy", policy(nonce));
        filterChain.doFilter(request, response);
    }

    private String createNonce() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String policy(String nonce) {
        return "default-src 'self'; "
                + "base-uri 'self'; "
                + "object-src 'none'; "
                + "frame-ancestors 'self'; "
                + "form-action 'self'; "
                + "script-src 'self' " + NONCE_SOURCE_PREFIX + nonce + "'; "
                + "script-src-attr 'none'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "font-src 'self'; "
                + "img-src 'self' data: https:; "
                + "connect-src 'self'";
    }
}
