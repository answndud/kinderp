package com.kinderp.domain.notification.repository;

import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.NotificationChannel;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long>, NotificationOutboxQueryRepository {

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

    /**
     * 발송 결과 기록은 "내 claim token이 아직 유효한 PROCESSING 행"에만 반영한다.
     * 반환값이 0이면 timeout 뒤 다른 worker가 재claim한 것이므로 결과를 버린다 (fencing).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationOutbox o
               SET o.status = com.kinderp.domain.notification.entity.NotificationDeliveryStatus.DELIVERED,
                   o.deliveredAt = :now,
                   o.processingStartedAt = null,
                   o.claimToken = null,
                   o.lastError = null
             WHERE o.id = :id
               AND o.claimToken = :claimToken
               AND o.status = com.kinderp.domain.notification.entity.NotificationDeliveryStatus.PROCESSING
            """)
    int markDeliveredIfClaimed(@Param("id") Long id,
                               @Param("claimToken") String claimToken,
                               @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationOutbox o
               SET o.status = com.kinderp.domain.notification.entity.NotificationDeliveryStatus.PENDING,
                   o.nextAttemptAt = :nextAttemptAt,
                   o.processingStartedAt = null,
                   o.claimToken = null,
                   o.lastError = :lastError
             WHERE o.id = :id
               AND o.claimToken = :claimToken
               AND o.status = com.kinderp.domain.notification.entity.NotificationDeliveryStatus.PROCESSING
            """)
    int scheduleRetryIfClaimed(@Param("id") Long id,
                               @Param("claimToken") String claimToken,
                               @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                               @Param("lastError") String lastError);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationOutbox o
               SET o.status = com.kinderp.domain.notification.entity.NotificationDeliveryStatus.DEAD_LETTER,
                   o.deadLetteredAt = :now,
                   o.processingStartedAt = null,
                   o.claimToken = null,
                   o.lastError = :lastError
             WHERE o.id = :id
               AND o.claimToken = :claimToken
               AND o.status = com.kinderp.domain.notification.entity.NotificationDeliveryStatus.PROCESSING
            """)
    int markDeadLetterIfClaimed(@Param("id") Long id,
                                @Param("claimToken") String claimToken,
                                @Param("now") LocalDateTime now,
                                @Param("lastError") String lastError);

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
}
