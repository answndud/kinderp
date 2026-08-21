package com.kinderp.domain.notification.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties({NotificationDeliveryProperties.class, NotificationRateLimitProperties.class})
public class NotificationConfig {

    @Bean
    public RestTemplate notificationRestTemplate(RestTemplateBuilder restTemplateBuilder,
                                                 NotificationDeliveryProperties deliveryProperties) {
        return restTemplateBuilder
                .connectTimeout(deliveryProperties.getConnectTimeout())
                .readTimeout(deliveryProperties.getReadTimeout())
                .build();
    }
}
