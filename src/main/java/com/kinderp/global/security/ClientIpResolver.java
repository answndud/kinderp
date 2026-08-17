package com.kinderp.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private final ClientIpProperties properties;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddr)) {
            return fallback(remoteAddr);
        }

        String forwardedFor = extractForwardedFor(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor;
        }

        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        return fallback(remoteAddr);
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        if (properties.getTrustedProxies().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .anyMatch(proxy -> matches(proxy, remoteAddr))) {
            return true;
        }

        return matches("127.0.0.1/32", remoteAddr) || matches("::1/128", remoteAddr);
    }

    private boolean matches(String trustedProxy, String remoteAddr) {
        try {
            return new IpAddressMatcher(trustedProxy).matches(remoteAddr);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String extractForwardedFor(String forwardedForHeader) {
        if (forwardedForHeader == null || forwardedForHeader.isBlank()) {
            return null;
        }

        String[] candidates = forwardedForHeader.split(",");
        for (String candidate : candidates) {
            String normalized = normalizeIp(candidate);
            if (normalized != null && !UNKNOWN.equalsIgnoreCase(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String fallback(String remoteAddr) {
        return remoteAddr == null ? UNKNOWN : remoteAddr;
    }
}
