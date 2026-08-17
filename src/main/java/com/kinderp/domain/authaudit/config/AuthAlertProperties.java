package com.kinderp.domain.authaudit.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.auth-alert")
public class AuthAlertProperties {

    private boolean enabled = true;

    private long loginFailureThreshold = 3L;

    private Duration loginFailureWindow = Duration.ofMinutes(10);

    private Duration alertCooldown = Duration.ofMinutes(30);
}
