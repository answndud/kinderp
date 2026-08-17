package com.kinderp.domain.notification.dto.response;

import com.kinderp.domain.notification.entity.Notification;
import com.kinderp.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String typeDescription,
        String title,
        String content,
        String linkUrl,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        String relatedEntityType,
        Long relatedEntityId
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getType() != null ? notification.getType().getDescription() : null,
                notification.getTitle(),
                notification.getContent(),
                notification.getLinkUrl(),
                notification.getIsRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getRelatedEntityType(),
                notification.getRelatedEntityId()
        );
    }
}
