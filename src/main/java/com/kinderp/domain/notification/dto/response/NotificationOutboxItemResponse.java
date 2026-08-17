package com.kinderp.domain.notification.dto.response;

import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.entity.NotificationChannel;
import java.time.LocalDateTime;

public record NotificationOutboxItemResponse(
        Long id,
        Long notificationId,
        NotificationChannel channel,
        NotificationDeliveryStatus status,
        NotificationType notificationType,
        Long receiverMemberId,
        String receiverEmail,
        String title,
        int attemptCount,
        int maxAttempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime deadLetteredAt,
        String lastError
) {
    public static NotificationOutboxItemResponse from(NotificationOutbox outbox) {
        return new NotificationOutboxItemResponse(
                outbox.getId(),
                outbox.getNotification() != null ? outbox.getNotification().getId() : null,
                outbox.getChannel(),
                outbox.getStatus(),
                outbox.getNotificationType(),
                outbox.getReceiverMemberId(),
                outbox.getReceiverEmail(),
                outbox.getTitle(),
                outbox.getAttemptCount(),
                outbox.getMaxAttempts(),
                outbox.getNextAttemptAt(),
                outbox.getLastAttemptAt(),
                outbox.getDeadLetteredAt(),
                outbox.getLastError()
        );
    }
}
