package com.kinderp.domain.notification.repository;

import com.kinderp.domain.notification.entity.Notification;
import com.kinderp.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 수신자별 알림 목록 조회 (최신순)
     */
    List<Notification> findByReceiverIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId);

    /**
     * 수신자별 알림 목록 조회 (페이징)
     */
    List<Notification> findByReceiverIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    Optional<Notification> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 안 읽은 알림 개수
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.receiver.id = :receiverId AND n.isRead = false AND n.deletedAt IS NULL")
    long countUnreadByReceiverId(@Param("receiverId") Long receiverId);

    /**
     * 특정 타입의 알림 조회
     */
    List<Notification> findByReceiverIdAndTypeAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId, NotificationType type);

    /**
     * 특정 타입의 알림 조회 (페이징)
     */
    List<Notification> findByReceiverIdAndTypeAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId, NotificationType type, Pageable pageable);

    /**
     * 연관 엔티티별 알림 조회
     */
    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId AND n.relatedEntityType = :entityType AND n.relatedEntityId = :entityId AND n.deletedAt IS NULL")
    List<Notification> findByRelatedEntity(@Param("receiverId") Long receiverId,
                                           @Param("entityType") String entityType,
                                           @Param("entityId") Long entityId);

    /**
     * 안 읽은 알림 목록
     */
    List<Notification> findByReceiverIdAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId);

    /**
     * 안 읽은 알림 목록 (페이징)
     */
    List<Notification> findByReceiverIdAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    /**
     * 안 읽은 알림 목록 (타입)
     */
    List<Notification> findByReceiverIdAndTypeAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId, NotificationType type, Pageable pageable);

    /**
     * 특정 시간 이후의 알림 조회
     */
    List<Notification> findByReceiverIdAndCreatedAtAfterAndDeletedAtIsNullOrderByCreatedAtDesc(Long receiverId, LocalDateTime after);

    /**
     * 일괄 읽음 표시
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.receiver.id = :receiverId AND n.isRead = false AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("receiverId") Long receiverId, @Param("readAt") LocalDateTime readAt);

    /**
     * 오래된 알림 삭제 (Soft Delete)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.deletedAt = :deletedAt WHERE n.receiver.id = :receiverId AND n.isRead = true AND n.createdAt < :beforeDate AND n.deletedAt IS NULL")
    int softDeleteOldReadNotifications(@Param("receiverId") Long receiverId,
                                       @Param("beforeDate") LocalDateTime beforeDate,
                                       @Param("deletedAt") LocalDateTime deletedAt);
}
