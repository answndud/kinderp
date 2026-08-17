package com.kinderp.global.config;

import com.kinderp.domain.notification.config.NotificationDeliveryProperties;
import com.kinderp.global.security.CorsProperties;
import com.kinderp.global.security.ManagementSurfaceProperties;
import com.kinderp.global.security.jwt.JwtProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StartupSafetyValidator {

    private static final String LEGACY_JWT_FALLBACK_SECRET =
            "your-256-bit-secret-key-here-must-be-at-least-32-characters";
    private static final Set<String> ALLOWED_RUNTIME_PROFILES = Set.of("local", "demo", "prod", "test");

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final ManagementSurfaceProperties managementSurfaceProperties;
    private final CorsProperties corsProperties;
    private final SeedProperties seedProperties;
    private final NotificationDeliveryProperties notificationDeliveryProperties;

    @PostConstruct
    void validate() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            throw new IllegalStateException(
                    "No active Spring profile configured. Use --spring.profiles.active=local|demo|prod."
            );
        }

        boolean hasKnownRuntimeProfile = Arrays.stream(activeProfiles).anyMatch(ALLOWED_RUNTIME_PROFILES::contains);
        if (!hasKnownRuntimeProfile) {
            throw new IllegalStateException(
                    "Unsupported runtime profile. Expected one of local, demo, prod, test."
            );
        }

        if (isActive("prod")) {
            validateProdSafety();
        }
    }

    private void validateProdSafety() {
        String jwtSecret = jwtProperties.getSecret();
        if (jwtSecret == null || jwtSecret.isBlank() || LEGACY_JWT_FALLBACK_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("Production profile requires a real JWT_SECRET.");
        }

        if (!jwtProperties.isCookieSecure()) {
            throw new IllegalStateException("Production profile must keep jwt.cookie-secure=true.");
        }

        boolean publicDemoEnabled = environment.getProperty("APP_PUBLIC_DEMO_ENABLED", Boolean.class, false);
        if (seedProperties.isEnabled() && !publicDemoEnabled) {
            throw new IllegalStateException("Production profile must keep app.seed.enabled=false.");
        }

        if (managementSurfaceProperties.isPublicApiDocs()) {
            throw new IllegalStateException("Production profile must not expose Swagger/OpenAPI publicly.");
        }

        if (managementSurfaceProperties.isExposePrometheusOnAppPort()) {
            throw new IllegalStateException("Production profile must not expose Prometheus on the app port.");
        }

        if (environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Production profile must keep springdoc.api-docs.enabled=false.");
        }

        if (environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Production profile must keep springdoc.swagger-ui.enabled=false.");
        }

        validateProdCors();
        validateProdWebhooks();
    }

    private void validateProdCors() {
        for (String origin : corsProperties.resolveAllowedOrigins()) {
            if ("*".equals(origin)) {
                throw new IllegalStateException("Production profile must not use wildcard CORS origins.");
            }
            if (!origin.startsWith("https://")) {
                throw new IllegalStateException("Production profile requires HTTPS CORS origins.");
            }
        }
    }

    private void validateProdWebhooks() {
        validateWebhook("push", notificationDeliveryProperties.getPush());
        validateWebhook("app", notificationDeliveryProperties.getApp());
        validateWebhook("incident-webhook", notificationDeliveryProperties.getIncidentWebhook());
    }

    private void validateWebhook(String name, NotificationDeliveryProperties.Webhook webhook) {
        if (!webhook.isEnabled()) {
            return;
        }
        if (webhook.getWebhookUrl() == null || !webhook.getWebhookUrl().startsWith("https://")) {
            throw new IllegalStateException("Production webhook must use HTTPS: " + name);
        }
        if (webhook.getSignatureSecret() == null || webhook.getSignatureSecret().isBlank()) {
            throw new IllegalStateException("Production webhook requires a signature secret: " + name);
        }
    }

    private boolean isActive(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
