package com.kinderp.domain.notification.config;

import com.kinderp.domain.notification.entity.NotificationType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.delivery")
public class NotificationDeliveryProperties {

    private int batchSize = 500;
    private boolean workerEnabled = true;
    private long workerFixedDelayMs = 15000L;
    private int workerBatchSize = 100;
    private int maxAttempts = 5;
    private Duration initialRetryDelay = Duration.ofSeconds(30);
    private Duration maxRetryDelay = Duration.ofMinutes(15);
    private double retryBackoffMultiplier = 2.0d;
    private Duration processingTimeout = Duration.ofMinutes(5);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private List<NotificationType> externalTypes = new ArrayList<>(List.of(NotificationType.AUTH_ANOMALY_DETECTED));
    private List<NotificationType> incidentTypes = new ArrayList<>(List.of(NotificationType.AUTH_ANOMALY_DETECTED));
    private Email email = new Email();
    private Webhook push = new Webhook();
    private Webhook app = new Webhook();
    private Webhook incidentWebhook = new Webhook();

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = false;
        private String from;
        private String subjectPrefix = "[KinderCare]";
    }

    @Getter
    @Setter
    public static class Webhook {
        private boolean enabled = false;
        private String webhookUrl;
        private String signatureSecret;
    }
}
