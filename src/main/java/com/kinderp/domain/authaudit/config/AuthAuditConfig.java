package com.kinderp.domain.authaudit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        AuthAlertProperties.class,
        AuthAuditRetentionProperties.class
})
public class AuthAuditConfig {
}
