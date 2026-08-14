package com.erp.integration;

import com.erp.common.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class ObservabilityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    private HealthContributor originalCriticalDependencies;

    @AfterEach
    void restoreCriticalDependenciesContributor() {
        if (originalCriticalDependencies == null) {
            return;
        }
        healthContributorRegistry.unregisterContributor("criticalDependencies");
        healthContributorRegistry.registerContributor("criticalDependencies", originalCriticalDependencies);
        originalCriticalDependencies = null;
    }

    @Test
    @DisplayName("Actuator health는 인증 없이 조회할 수 있다")
    void actuatorHealth_IsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("Actuator readiness probe는 활성화되어 있다")
    void actuatorReadinessProbe_IsEnabled() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator info는 실행 중인 앱 버전을 식별할 수 있다")
    void actuatorInfo_ExposesApplicationVersion() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("erp"))
                .andExpect(jsonPath("$.app.version").value("0.0.1-SNAPSHOT"));
    }

    @Test
    @DisplayName("readiness probe는 critical dependency가 DOWN이면 서비스 불가로 전환된다")
    void actuatorReadinessProbe_IsDown_WhenCriticalDependenciesFail() throws Exception {
        replaceCriticalDependencies(() -> Health.down()
                .withDetail("database", "simulated-down")
                .withDetail("redis", "up")
                .build());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    @DisplayName("liveness probe는 critical dependency 장애와 분리되어 UP을 유지한다")
    void actuatorLivenessProbe_StaysUp_WhenCriticalDependenciesFail() throws Exception {
        replaceCriticalDependencies(() -> Health.down()
                .withDetail("database", "simulated-down")
                .withDetail("redis", "up")
                .build());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Prometheus endpoint는 기본 설정에서 app port에 노출되지 않는다")
    void actuatorPrometheus_IsNotExposed_OnAppPortByDefault() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(authenticated(principalMember)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Swagger UI와 OpenAPI 문서는 기본 설정에서 비활성화된다")
    void swaggerUi_AndApiDocs_AreDisabledByDefault() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")
                        .with(authenticated(principalMember)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v3/api-docs")
                        .with(authenticated(principalMember)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("요청 correlation id를 응답 헤더로 그대로 돌려준다")
    void correlationIdHeader_IsEchoedBack() throws Exception {
        mockMvc.perform(get("/login")
                        .header("X-Correlation-Id", "demo-correlation-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "demo-correlation-123"));
    }

    private void replaceCriticalDependencies(HealthIndicator healthIndicator) {
        if (originalCriticalDependencies == null) {
            originalCriticalDependencies = healthContributorRegistry.unregisterContributor("criticalDependencies");
        } else {
            healthContributorRegistry.unregisterContributor("criticalDependencies");
        }
        healthContributorRegistry.registerContributor("criticalDependencies", healthIndicator);
    }
}
