package com.erp.domain.notification.service.channel;

import com.erp.domain.notification.config.NotificationDeliveryProperties;
import com.erp.domain.notification.entity.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.delivery.push", name = "enabled", havingValue = "true")
public class PushNotificationSender implements NotificationChannelSender {

    private final NotificationDeliveryProperties deliveryProperties;
    private final RestTemplate notificationRestTemplate;
    private final WebhookRequestSigner webhookRequestSigner;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(NotificationDeliveryPayload payload) {
        String webhookUrl = deliveryProperties.getPush().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException("notification.delivery.push.webhook-url is not configured");
        }

        WebhookNotificationPayload body = WebhookNotificationPayload.from(payload);
        HttpHeaders headers = webhookRequestSigner.createHeaders(
                body,
                deliveryProperties.getPush().getSignatureSecret()
        );

        ResponseEntity<String> response = notificationRestTemplate.postForEntity(
                webhookUrl,
                new HttpEntity<>(body, headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Push notification failed with status " + response.getStatusCode());
        }

        log.debug("Push notification dispatched. receiverId={}, status={}", payload.receiverId(), response.getStatusCode());
    }
}
