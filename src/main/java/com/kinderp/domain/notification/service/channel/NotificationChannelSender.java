package com.kinderp.domain.notification.service.channel;

import com.kinderp.domain.notification.entity.NotificationChannel;
import java.util.List;

public interface NotificationChannelSender {

    NotificationChannel channel();

    void send(NotificationDeliveryPayload payload);

    default void sendBatch(List<NotificationDeliveryPayload> payloads) {
        for (NotificationDeliveryPayload payload : payloads) {
            send(payload);
        }
    }
}
