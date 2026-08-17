package com.kinderp.domain.auth.service;

import com.kinderp.domain.auth.dto.response.AuthSessionResponse;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionRegistryService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:session:";
    private static final String REFRESH_SESSION_SET_KEY_PREFIX = "refresh:sessions:";
    private static final String SESSION_METADATA_KEY_PREFIX = "refresh:session:meta:";
    private static final long LAST_SEEN_TOUCH_INTERVAL_MS = 60_000L;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final RedisTemplate<String, Object> redisTemplate;

    public void registerSession(Long memberId,
                                String sessionId,
                                String refreshToken,
                                long refreshTtlMs,
                                MemberAuthProvider provider,
                                String clientIp,
                                String userAgent) {
        long now = System.currentTimeMillis();
        saveRefreshToken(memberId, sessionId, refreshToken, refreshTtlMs);
        saveMetadata(
                memberId,
                new SessionMetadata(
                        sessionId,
                        resolveProviderName(provider),
                        sanitize(clientIp),
                        sanitizeUserAgent(userAgent),
                        now,
                        now,
                        now,
                        now + refreshTtlMs
                ),
                refreshTtlMs
        );
    }

    public void rotateSession(Long memberId,
                              String sessionId,
                              String refreshToken,
                              long refreshTtlMs,
                              MemberAuthProvider provider,
                              String clientIp,
                              String userAgent) {
        long now = System.currentTimeMillis();
        SessionMetadata existing = loadMetadata(memberId, sessionId)
                .orElseGet(() -> new SessionMetadata(
                        sessionId,
                        resolveProviderName(provider),
                        sanitize(clientIp),
                        sanitizeUserAgent(userAgent),
                        now,
                        now,
                        now,
                        now + refreshTtlMs
                ));

        saveRefreshToken(memberId, sessionId, refreshToken, refreshTtlMs);
        saveMetadata(
                memberId,
                new SessionMetadata(
                        sessionId,
                        resolveProviderName(provider, existing.getSignInMethod()),
                        preferNonBlank(clientIp, existing.getClientIp()),
                        preferNonBlank(sanitizeUserAgent(userAgent), existing.getUserAgent()),
                        existing.getCreatedAtEpochMs(),
                        Math.max(existing.getLastSeenAtEpochMs(), now),
                        now,
                        now + refreshTtlMs
                ),
                refreshTtlMs
        );
    }

    public Optional<String> getStoredRefreshToken(Long memberId, String sessionId) {
        Object storedRefreshToken = redisTemplate.opsForValue().get(getRefreshTokenKey(memberId, sessionId));
        if (storedRefreshToken instanceof String token) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    public boolean isSessionActive(Long memberId, String sessionId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getRefreshTokenKey(memberId, sessionId)));
        } catch (RuntimeException ex) {
            log.warn("Failed to validate auth session activity for memberId={}, sessionId={}", memberId, sessionId, ex);
            return false;
        }
    }

    public void touchSession(Long memberId, String sessionId, String clientIp, String userAgent) {
        try {
            Long ttlMs = redisTemplate.getExpire(getRefreshTokenKey(memberId, sessionId), TimeUnit.MILLISECONDS);
            if (ttlMs == null || ttlMs <= 0) {
                return;
            }

            long now = System.currentTimeMillis();
            SessionMetadata metadata = loadMetadata(memberId, sessionId)
                    .orElseGet(() -> new SessionMetadata(
                            sessionId,
                            MemberAuthProvider.LOCAL.name(),
                            sanitize(clientIp),
                            sanitizeUserAgent(userAgent),
                            now,
                            now,
                            now,
                            now + ttlMs
                    ));

            if (now - metadata.getLastSeenAtEpochMs() < LAST_SEEN_TOUCH_INTERVAL_MS) {
                return;
            }

            saveMetadata(
                    memberId,
                    new SessionMetadata(
                            sessionId,
                            metadata.getSignInMethod(),
                            preferNonBlank(clientIp, metadata.getClientIp()),
                            preferNonBlank(sanitizeUserAgent(userAgent), metadata.getUserAgent()),
                            metadata.getCreatedAtEpochMs(),
                            now,
                            metadata.getLastRefreshedAtEpochMs(),
                            now + ttlMs
                    ),
                    ttlMs
            );
        } catch (RuntimeException ex) {
            log.debug("Skipped auth session touch for memberId={}, sessionId={}", memberId, sessionId, ex);
        }
    }

    public List<AuthSessionResponse> getActiveSessions(Long memberId, String currentSessionId) {
        Set<Object> rawSessionIds = redisTemplate.opsForSet().members(getRefreshSessionSetKey(memberId));
        if (rawSessionIds == null || rawSessionIds.isEmpty()) {
            return List.of();
        }

        List<AuthSessionResponse> sessions = new ArrayList<>();
        for (String sessionId : rawSessionIds.stream().map(String::valueOf).collect(Collectors.toSet())) {
            Long ttlMs = redisTemplate.getExpire(getRefreshTokenKey(memberId, sessionId), TimeUnit.MILLISECONDS);
            if (ttlMs == null || ttlMs <= 0) {
                removeSessionKeyReferences(memberId, sessionId);
                continue;
            }

            SessionMetadata metadata = loadMetadata(memberId, sessionId)
                    .orElseGet(() -> new SessionMetadata(
                            sessionId,
                            MemberAuthProvider.LOCAL.name(),
                            "unknown",
                            "Unknown device",
                            0L,
                            0L,
                            0L,
                            System.currentTimeMillis() + ttlMs
                    ));

            sessions.add(new AuthSessionResponse(
                    sessionId,
                    metadata.getSignInMethod(),
                    resolveSignInMethodLabel(metadata.getSignInMethod()),
                    Objects.equals(currentSessionId, sessionId),
                    resolveDeviceLabel(metadata.getUserAgent()),
                    metadata.getClientIp(),
                    metadata.getUserAgent(),
                    toInstant(metadata.getCreatedAtEpochMs()),
                    toInstant(metadata.getLastSeenAtEpochMs()),
                    toInstant(metadata.getLastRefreshedAtEpochMs()),
                    toInstant(metadata.getExpiresAtEpochMs())
            ));
        }

        return sessions.stream()
                .sorted(Comparator
                        .comparing(AuthSessionResponse::current).reversed()
                        .thenComparing(session -> Optional.ofNullable(session.lastSeenAt()).orElse(Instant.EPOCH), Comparator.reverseOrder())
                        .thenComparing(session -> Optional.ofNullable(session.createdAt()).orElse(Instant.EPOCH), Comparator.reverseOrder()))
                .toList();
    }

    public void revokeSession(Long memberId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        removeSessionKeyReferences(memberId, sessionId);
    }

    public void revokeOtherSessions(Long memberId, String currentSessionId) {
        Set<Object> rawSessionIds = redisTemplate.opsForSet().members(getRefreshSessionSetKey(memberId));
        if (rawSessionIds == null || rawSessionIds.isEmpty()) {
            return;
        }

        for (Object rawSessionId : rawSessionIds) {
            if (rawSessionId == null) {
                continue;
            }

            String sessionId = rawSessionId.toString();
            if (Objects.equals(sessionId, currentSessionId)) {
                continue;
            }
            removeSessionKeyReferences(memberId, sessionId);
        }
    }

    public void revokeAllSessions(Long memberId) {
        Set<Object> rawSessionIds = redisTemplate.opsForSet().members(getRefreshSessionSetKey(memberId));
        if (rawSessionIds != null) {
            for (Object rawSessionId : rawSessionIds) {
                if (rawSessionId != null) {
                    removeSessionKeyReferences(memberId, rawSessionId.toString());
                }
            }
        }
        redisTemplate.delete(getRefreshSessionSetKey(memberId));
    }

    private void saveRefreshToken(Long memberId, String sessionId, String refreshToken, long ttlMs) {
        redisTemplate.opsForValue().set(
                getRefreshTokenKey(memberId, sessionId),
                refreshToken,
                ttlMs,
                TimeUnit.MILLISECONDS
        );
        redisTemplate.opsForSet().add(getRefreshSessionSetKey(memberId), sessionId);
        redisTemplate.expire(getRefreshSessionSetKey(memberId), ttlMs, TimeUnit.MILLISECONDS);
    }

    private void saveMetadata(Long memberId, SessionMetadata metadata, long ttlMs) {
        redisTemplate.opsForValue().set(getSessionMetadataKey(memberId, metadata.getSessionId()), metadata, ttlMs, TimeUnit.MILLISECONDS);
    }

    private Optional<SessionMetadata> loadMetadata(Long memberId, String sessionId) {
        Object rawValue = redisTemplate.opsForValue().get(getSessionMetadataKey(memberId, sessionId));
        if (rawValue instanceof SessionMetadata metadata) {
            return Optional.of(metadata);
        }
        return Optional.empty();
    }

    private void removeSessionKeyReferences(Long memberId, String sessionId) {
        redisTemplate.delete(getRefreshTokenKey(memberId, sessionId));
        redisTemplate.delete(getSessionMetadataKey(memberId, sessionId));

        String sessionSetKey = getRefreshSessionSetKey(memberId);
        redisTemplate.opsForSet().remove(sessionSetKey, sessionId);
        Long remainingSessions = redisTemplate.opsForSet().size(sessionSetKey);
        if (remainingSessions == null || remainingSessions == 0) {
            redisTemplate.delete(sessionSetKey);
        }
    }

    private String getRefreshTokenKey(Long memberId, String sessionId) {
        return REFRESH_TOKEN_KEY_PREFIX + memberId + ":" + sessionId;
    }

    private String getRefreshSessionSetKey(Long memberId) {
        return REFRESH_SESSION_SET_KEY_PREFIX + memberId;
    }

    private String getSessionMetadataKey(Long memberId, String sessionId) {
        return SESSION_METADATA_KEY_PREFIX + memberId + ":" + sessionId;
    }

    private Instant toInstant(long epochMs) {
        if (epochMs <= 0L) {
            return null;
        }
        return Instant.ofEpochMilli(epochMs);
    }

    private String resolveDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || "Unknown device".equals(userAgent)) {
            return "알 수 없는 기기";
        }

        String normalized = userAgent.toLowerCase(Locale.ROOT);
        String browser = normalized.contains("edg/") ? "Edge"
                : normalized.contains("chrome/") ? "Chrome"
                : normalized.contains("safari/") && !normalized.contains("chrome/") ? "Safari"
                : normalized.contains("firefox/") ? "Firefox"
                : normalized.contains("kakaotalk") ? "KakaoTalk"
                : "Browser";

        String os = normalized.contains("windows") ? "Windows"
                : normalized.contains("mac os x") || normalized.contains("macintosh") ? "macOS"
                : normalized.contains("android") ? "Android"
                : normalized.contains("iphone") || normalized.contains("ipad") || normalized.contains("ios") ? "iOS"
                : normalized.contains("linux") ? "Linux"
                : "Unknown OS";

        return browser + " on " + os;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }

        String sanitized = userAgent.trim();
        if (sanitized.length() > MAX_USER_AGENT_LENGTH) {
            return sanitized.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return sanitized;
    }

    private String preferNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank() && !"unknown".equalsIgnoreCase(preferred)) {
            return preferred.trim();
        }
        return fallback;
    }

    private String resolveProviderName(MemberAuthProvider provider) {
        return resolveProviderName(provider, MemberAuthProvider.LOCAL.name());
    }

    private String resolveProviderName(MemberAuthProvider provider, String fallback) {
        if (provider == null) {
            return fallback;
        }
        return provider.name();
    }

    private String resolveSignInMethodLabel(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return "Local";
        }

        try {
            return switch (MemberAuthProvider.valueOf(providerName)) {
                case GOOGLE -> "Google";
                case KAKAO -> "Kakao";
                case LOCAL -> "Local";
            };
        } catch (IllegalArgumentException ex) {
            return providerName;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionMetadata {
        private String sessionId;
        private String signInMethod;
        private String clientIp;
        private String userAgent;
        private long createdAtEpochMs;
        private long lastSeenAtEpochMs;
        private long lastRefreshedAtEpochMs;
        private long expiresAtEpochMs;
    }
}
