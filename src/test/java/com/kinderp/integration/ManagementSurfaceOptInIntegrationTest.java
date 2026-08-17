package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "app.security.management-surface.public-api-docs=true",
        "app.security.management-surface.expose-prometheus-on-app-port=true"
})
class ManagementSurfaceOptInIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthAuditLogService authAuditLogService;

    @Test
    @DisplayName("Swagger UI와 OpenAPI 문서는 명시적으로 열었을 때만 공개된다")
    void swaggerUi_AndApiDocs_ArePublic_WhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("KinderP API"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    @DisplayName("Prometheus endpoint는 app port 노출을 명시적으로 켰을 때만 열린다")
    void actuatorPrometheus_IsPublic_WhenExplicitlyEnabled() throws Exception {
        authAuditLogService.recordLoginSuccess(
                principalMember.getId(),
                principalMember.getEmail(),
                MemberAuthProvider.LOCAL,
                "198.51.100.10"
        );

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("erp_auth_events_total")))
                .andExpect(content().string(containsString("event_type=\"login\"")))
                .andExpect(content().string(containsString("result=\"success\"")))
                .andExpect(content().string(containsString("provider=\"local\"")));
    }
}
