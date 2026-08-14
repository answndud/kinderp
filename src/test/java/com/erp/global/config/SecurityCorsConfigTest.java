package com.erp.global.config;

import com.erp.common.TestcontainersSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.cors.allowed-origins=https://erp.example.com,https://admin.example.com")
@Tag("integration")
class SecurityCorsConfigTest extends TestcontainersSupport {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void corsAllowedOriginsAreLoadedFromProperties() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        CorsConfiguration configuration = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://erp.example.com", "https://admin.example.com");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedHeaders())
                .contains("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "HX-Request")
                .doesNotContain("*");
    }
}
