package com.erp.global.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppInfoConfig {

    @Bean
    InfoContributor appInfoContributor(
            @Value("${spring.application.name}") String appName,
            @Value("${info.app.description}") String description,
            @Value("${info.app.version}") String version) {
        return builder -> builder.withDetail("app", Map.of(
                "name", appName,
                "description", description,
                "version", version
        ));
    }
}
