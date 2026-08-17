package com.kinderp.global.monitoring;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(PrometheusMeterRegistry.class)
@ConditionalOnProperty(
        prefix = "app.security.management-surface",
        name = "expose-prometheus-on-app-port",
        havingValue = "true"
)
@ConditionalOnMissingBean(type = "org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint")
public class PrometheusScrapeController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public PrometheusScrapeController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @GetMapping(value = "/actuator/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String scrape() {
        return prometheusMeterRegistry.scrape();
    }
}
