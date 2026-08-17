package com.kinderp.domain.notification.service;

import com.kinderp.domain.notification.config.NotificationDeliveryProperties;
import com.kinderp.domain.notification.entity.Notification;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.entity.NotificationChannel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryPolicyService {

    private final NotificationDeliveryProperties deliveryProperties;

    public List<NotificationChannel> resolveReceiverChannels(Notification notification) {
        if (notification == null || notification.getType() == null) {
            return List.of();
        }

        Set<NotificationChannel> channels = new LinkedHashSet<>();
        if (deliveryProperties.getExternalTypes().contains(notification.getType())) {
            if (deliveryProperties.getEmail().isEnabled() && hasReceiverEmail(notification)) {
                channels.add(NotificationChannel.EMAIL);
            }
            if (deliveryProperties.getPush().isEnabled()) {
                channels.add(NotificationChannel.PUSH);
            }
            if (deliveryProperties.getApp().isEnabled()) {
                channels.add(NotificationChannel.APP);
            }
        }

        return List.copyOf(channels);
    }

    public List<NotificationChannel> resolveIncidentChannels(NotificationType notificationType) {
        if (notificationType == null) {
            return List.of();
        }

        Set<NotificationChannel> channels = new LinkedHashSet<>();
        if (deliveryProperties.getIncidentTypes().contains(notificationType)
                && deliveryProperties.getIncidentWebhook().isEnabled()) {
            channels.add(NotificationChannel.INCIDENT_WEBHOOK);
        }

        return List.copyOf(channels);
    }

    public List<NotificationChannel> resolveChannels(Notification notification) {
        if (notification == null) {
            return List.of();
        }

        Set<NotificationChannel> channels = new LinkedHashSet<>();
        channels.addAll(resolveReceiverChannels(notification));
        channels.addAll(resolveIncidentChannels(notification.getType()));
        return List.copyOf(channels);
    }

    private boolean hasReceiverEmail(Notification notification) {
        return notification.getReceiver() != null
                && notification.getReceiver().getEmail() != null
                && !notification.getReceiver().getEmail().isBlank();
    }
}
