package com.erp.domain.notification.repository;

import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.entity.NotificationOutbox;
import com.erp.domain.notification.entity.NotificationChannel;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
            NotificationDeliveryStatus status,
            LocalDateTime now,
            Pageable pageable
    );

    List<NotificationOutbox> findByStatusAndProcessingStartedAtLessThanEqualOrderByProcessingStartedAtAscIdAsc(
            NotificationDeliveryStatus status,
            LocalDateTime staleBefore,
            Pageable pageable
    );

    @Query(value = """
            SELECT id
            FROM notification_outbox
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> claimPendingIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query(value = """
            SELECT id
            FROM notification_outbox
            WHERE status = 'PROCESSING'
              AND processing_started_at <= :staleBefore
            ORDER BY processing_started_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> claimStaleProcessingIds(@Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);

    long countByNotificationReceiverKindergartenIdAndStatus(Long kindergartenId, NotificationDeliveryStatus status);

    long countByNotificationReceiverKindergartenIdAndStatusAndChannel(
            Long kindergartenId,
            NotificationDeliveryStatus status,
            NotificationChannel channel
    );

    long countByStatusAndChannel(NotificationDeliveryStatus status, NotificationChannel channel);

    long countByNotificationIdAndStatusIn(Long notificationId, Collection<NotificationDeliveryStatus> statuses);

    List<NotificationOutbox> findByNotificationIdOrderByIdAsc(Long notificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationOutbox> findByIdAndNotificationReceiverKindergartenId(Long id, Long kindergartenId);

    List<NotificationOutbox> findByIdIn(Collection<Long> ids);

    Optional<NotificationOutbox> findByNotificationIdAndChannel(Long notificationId, NotificationChannel channel);

    List<NotificationOutbox> findByChannelOrderByIdAsc(NotificationChannel channel);

    Page<NotificationOutbox> findByNotificationReceiverKindergartenIdAndStatusOrderByDeadLetteredAtDescIdDesc(
            Long kindergartenId,
            NotificationDeliveryStatus status,
            Pageable pageable
    );

    Page<NotificationOutbox> findByNotificationReceiverKindergartenIdAndStatusAndChannelOrderByDeadLetteredAtDescIdDesc(
            Long kindergartenId,
            NotificationDeliveryStatus status,
            NotificationChannel channel,
            Pageable pageable
    );

    @Query(value = """
            SELECT outbox
            FROM NotificationOutbox outbox
            WHERE outbox.notification.receiver.kindergarten.id = :kindergartenId
              AND (:status IS NULL OR outbox.status = :status)
              AND (:channel IS NULL OR outbox.channel = :channel)
              AND (
                    :keyword IS NULL
                    OR LOWER(outbox.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.receiverEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.receiverName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.lastError) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """,
            countQuery = """
            SELECT COUNT(outbox)
            FROM NotificationOutbox outbox
            WHERE outbox.notification.receiver.kindergarten.id = :kindergartenId
              AND (:status IS NULL OR outbox.status = :status)
              AND (:channel IS NULL OR outbox.channel = :channel)
              AND (
                    :keyword IS NULL
                    OR LOWER(outbox.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.receiverEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.receiverName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(outbox.lastError) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<NotificationOutbox> searchTimeline(
            @Param("kindergartenId") Long kindergartenId,
            @Param("status") NotificationDeliveryStatus status,
            @Param("channel") NotificationChannel channel,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
