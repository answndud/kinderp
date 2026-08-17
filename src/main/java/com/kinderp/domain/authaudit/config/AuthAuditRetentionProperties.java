package com.kinderp.domain.authaudit.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.auth-audit-retention")
public class AuthAuditRetentionProperties {

    private boolean enabled = true;

    private Duration archiveAfter = Duration.ofDays(30);

    private Duration deleteAfter = Duration.ofDays(365);

    private String cron = "0 30 3 * * *";

    private int batchSize = 500;
}
