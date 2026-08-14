package com.erp.domain.notification.entity;

import com.erp.domain.member.entity.Member;
import com.erp.domain.notification.service.channel.NotificationDeliveryPayload;
import com.erp.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_outbox_notification_channel",
                        columnNames = {"notification_id", "channel"}
                )
        },
        indexes = {
                @Index(name = "idx_notification_outbox_status_next_attempt", columnList = "status, next_attempt_at, id"),
                @Index(name = "idx_notification_outbox_processing_started", columnList = "status, processing_started_at, id"),
                @Index(name = "idx_notification_outbox_notification_id", columnList = "notification_id"),
                @Index(name = "idx_notification_outbox_timeline", columnList = "status, channel, created_at, id"),
                @Index(name = "idx_notification_outbox_dead_letter_timeline", columnList = "status, channel, dead_lettered_at, id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationDeliveryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "receiver_member_id")
    private Long receiverMemberId;

    @Column(name = "receiver_email")
    private String receiverEmail;

    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "dead_lettered_at")
    private LocalDateTime deadLetteredAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Builder
    private NotificationOutbox(
            Notification notification,
            NotificationChannel channel,
            NotificationDeliveryStatus status,
            NotificationType notificationType,
            Long receiverMemberId,
            String receiverEmail,
            String receiverName,
            String title,
            String content,
            String linkUrl,
            int attemptCount,
            int maxAttempts,
            LocalDateTime nextAttemptAt
    ) {
        this.notification = notification;
        this.channel = channel;
        this.status = status;
        this.notificationType = notificationType;
        this.receiverMemberId = receiverMemberId;
        this.receiverEmail = receiverEmail;
        this.receiverName = receiverName;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static NotificationOutbox create(Notification notification, NotificationChannel channel, int maxAttempts) {
        Member receiver = notification.getReceiver();
        return NotificationOutbox.builder()
                .notification(notification)
                .channel(channel)
                .status(NotificationDeliveryStatus.PENDING)
                .notificationType(notification.getType())
                .receiverMemberId(receiver != null ? receiver.getId() : null)
                .receiverEmail(receiver != null ? receiver.getEmail() : null)
                .receiverName(receiver != null ? receiver.getName() : null)
                .title(notification.getTitle())
                .content(notification.getContent())
                .linkUrl(notification.getLinkUrl())
                .attemptCount(0)
                .maxAttempts(Math.max(maxAttempts, 1))
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }

    public void markProcessing(LocalDateTime now) {
        this.status = NotificationDeliveryStatus.PROCESSING;
        this.processingStartedAt = now;
        this.lastAttemptAt = now;
        this.attemptCount += 1;
    }

    public void markDelivered(LocalDateTime now) {
        this.status = NotificationDeliveryStatus.DELIVERED;
        this.deliveredAt = now;
        this.processingStartedAt = null;
        this.lastError = null;
    }

    public void scheduleRetry(LocalDateTime nextAttemptAt, String errorMessage) {
        this.status = NotificationDeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.processingStartedAt = null;
        this.lastError = truncateError(errorMessage);
    }

    public void markDeadLetter(LocalDateTime now, String errorMessage) {
        this.status = NotificationDeliveryStatus.DEAD_LETTER;
        this.deadLetteredAt = now;
        this.processingStartedAt = null;
        this.lastError = truncateError(errorMessage);
    }

    public void resetDeadLetterForRetry(LocalDateTime now, int configuredMaxAttempts) {
        this.status = NotificationDeliveryStatus.PENDING;
        this.processingStartedAt = null;
        this.deadLetteredAt = null;
        this.lastError = null;
        this.nextAttemptAt = now;
        this.maxAttempts = Math.max(Math.max(configuredMaxAttempts, 1), this.attemptCount + 1);
    }

    public boolean canRetry() {
        return this.attemptCount < this.maxAttempts;
    }

    public NotificationDeliveryPayload toPayload() {
        return new NotificationDeliveryPayload(
                notification != null ? notification.getId() : null,
                receiverMemberId,
                receiverEmail,
                receiverName,
                notificationType,
                title,
                content,
                linkUrl
        );
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String normalized = errorMessage.trim();
        if (normalized.length() <= MAX_ERROR_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_LENGTH);
    }
}
