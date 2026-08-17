package com.kinderp.global.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.management-surface")
public class ManagementSurfaceProperties {

    /**
     * Swagger/OpenAPI를 로그인 없이 공개할지 여부.
     */
    private boolean publicApiDocs = false;

    /**
     * 애플리케이션 메인 포트에서 Prometheus 경로를 노출할지 여부.
     */
    private boolean exposePrometheusOnAppPort = false;
}
