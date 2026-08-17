package com.kinderp.domain.auth.dto.response;

import java.time.Instant;

public record AuthSessionResponse(
        String sessionId,
        String signInMethod,
        String signInMethodLabel,
        boolean current,
        String deviceLabel,
        String clientIp,
        String userAgent,
        Instant createdAt,
        Instant lastSeenAt,
        Instant lastRefreshedAt,
        Instant expiresAt
) {
}
