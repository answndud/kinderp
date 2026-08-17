package com.kinderp.global.config;

import com.kinderp.domain.notification.config.NotificationDeliveryProperties;
import com.kinderp.global.security.CorsProperties;
import com.kinderp.global.security.ManagementSurfaceProperties;
import com.kinderp.global.security.jwt.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartupSafetyValidatorTest {

    @Test
    @DisplayName("활성 프로파일이 없으면 부팅을 막는다")
    void validate_Fails_WhenNoActiveProfile() {
        StartupSafetyValidator validator = newValidator(
                new String[0],
                "local-dev-only-jwt-secret-key-at-least-32-bytes",
                true,
                false,
                false,
                false,
                false
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 공개 API 문서를 허용하지 않는다")
    void validate_Fails_WhenProdExposesPublicApiDocs() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                true,
                false,
                false,
                false
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 app port Prometheus 노출을 허용하지 않는다")
    void validate_Fails_WhenProdExposesPrometheusOnAppPort() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                true,
                false,
                false
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 Springdoc API 문서를 활성화할 수 없다")
    void validate_Fails_WhenProdEnablesSpringdocApiDocs() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                false,
                true,
                false
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 seed를 켤 수 없다")
    void validate_Fails_WhenProdEnablesSeed() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                false,
                false,
                true
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 legacy fallback JWT secret을 허용하지 않는다")
    void validate_Fails_WhenProdUsesLegacyJwtFallbackSecret() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "your-256-bit-secret-key-here-must-be-at-least-32-characters",
                true,
                false,
                false,
                false,
                false
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 insecure cookie를 허용하지 않는다")
    void validate_Fails_WhenProdDisablesSecureCookie() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                false,
                false,
                false,
                false,
                false
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 wildcard CORS origin을 허용하지 않는다")
    void validate_Fails_WhenProdUsesWildcardCorsOrigin() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                false,
                false,
                false,
                List.of("*")
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 HTTPS가 아닌 CORS origin을 허용하지 않는다")
    void validate_Fails_WhenProdUsesNonHttpsCorsOrigin() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                false,
                false,
                false,
                List.of("http://erp.example.com")
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("prod에서는 안전 설정 조합을 허용한다")
    void validate_Passes_ForSafeProdProfile() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                false,
                false,
                false,
                List.of("https://erp.example.com")
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("local에서는 명시적 프로파일과 개발용 fallback secret을 허용한다")
    void validate_Passes_ForLocalProfile() {
        StartupSafetyValidator validator = newValidator(
                new String[]{"local"},
                "local-dev-only-jwt-secret-key-at-least-32-bytes",
                false,
                true,
                true,
                true,
                false
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("prod webhook은 HTTPS와 서명 secret 없이는 허용하지 않는다")
    void validate_Fails_WhenProdWebhookIsUnsigned() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.getIncidentWebhook().setEnabled(true);
        properties.getIncidentWebhook().setWebhookUrl("https://hooks.example.com/incident");

        StartupSafetyValidator validator = newValidator(
                new String[]{"prod"},
                "prod-secret-key-at-least-32-bytes-long",
                true,
                false,
                false,
                false,
                false,
                List.of("https://erp.example.com"),
                properties
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    private StartupSafetyValidator newValidator(String[] activeProfiles,
                                                String jwtSecret,
                                                boolean cookieSecure,
                                                boolean publicApiDocs,
                                                boolean exposePrometheusOnAppPort,
                                                boolean apiDocsEnabled,
                                                boolean seedEnabled) {
        return newValidator(activeProfiles, jwtSecret, cookieSecure, publicApiDocs, exposePrometheusOnAppPort,
                apiDocsEnabled, seedEnabled, List.of("https://erp.example.com"));
    }

    private StartupSafetyValidator newValidator(String[] activeProfiles,
                                                String jwtSecret,
                                                boolean cookieSecure,
                                                boolean publicApiDocs,
                                                boolean exposePrometheusOnAppPort,
                                                boolean apiDocsEnabled,
                                                boolean seedEnabled,
                                                List<String> allowedOrigins) {
        return newValidator(activeProfiles, jwtSecret, cookieSecure, publicApiDocs, exposePrometheusOnAppPort,
                apiDocsEnabled, seedEnabled, allowedOrigins, new NotificationDeliveryProperties());
    }

    private StartupSafetyValidator newValidator(String[] activeProfiles,
                                                String jwtSecret,
                                                boolean cookieSecure,
                                                boolean publicApiDocs,
                                                boolean exposePrometheusOnAppPort,
                                                boolean apiDocsEnabled,
                                                boolean seedEnabled,
                                                List<String> allowedOrigins,
                                                NotificationDeliveryProperties notificationDeliveryProperties) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        environment.setProperty("springdoc.api-docs.enabled", Boolean.toString(apiDocsEnabled));
        environment.setProperty("springdoc.swagger-ui.enabled", Boolean.toString(apiDocsEnabled));

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(jwtSecret);
        jwtProperties.setCookieSecure(cookieSecure);

        ManagementSurfaceProperties managementSurfaceProperties = new ManagementSurfaceProperties();
        managementSurfaceProperties.setPublicApiDocs(publicApiDocs);
        managementSurfaceProperties.setExposePrometheusOnAppPort(exposePrometheusOnAppPort);

        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(allowedOrigins);

        SeedProperties seedProperties = new SeedProperties();
        seedProperties.setEnabled(seedEnabled);

        return new StartupSafetyValidator(environment, jwtProperties, managementSurfaceProperties,
                corsProperties, seedProperties, notificationDeliveryProperties);
    }
}
