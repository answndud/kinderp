package com.erp.domain.auth.service;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 인증 API rate limit 정책.
 * 기본값은 안전한 운영 기준이며 환경별로 명시적으로 조정할 수 있다.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.security.auth-rate-limit")
public class AuthRateLimitProperties {

    @NotNull
    private Duration loginWindow = Duration.ofMinutes(10);
    @NotNull
    private Duration refreshWindow = Duration.ofMinutes(5);
    @NotNull
    private Duration signupWindow = Duration.ofHours(1);

    @Positive
    private long loginIpLimit = 15;
    @Positive
    private long loginEmailLimit = 5;
    @Positive
    private long refreshIpLimit = 10;
    @Positive
    private long signupIpLimit = 10;
}
