package com.kinderp.domain.kidapplication.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kid-application")
public class KidApplicationWorkflowProperties {

    private Duration offerValidity = Duration.ofHours(72);
    private boolean expireOffersEnabled = true;
    private long expireOffersFixedDelayMs = 60000L;
}
