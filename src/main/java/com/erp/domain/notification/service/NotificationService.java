package com.erp.domain.notification.service;

import com.erp.domain.member.entity.Member;
import com.erp.domain.member.repository.MemberRepository;
import com.erp.domain.notification.config.NotificationDeliveryProperties;
import com.erp.domain.notification.dto.request.NotificationCreateRequest;
import com.erp.domain.notification.dto.response.NotificationResponse;
import com.erp.domain.notification.dto.response.UnreadCountResponse;
import com.erp.domain.notification.entity.Notification;
import com.erp.domain.notification.entity.NotificationType;
import com.erp.domain.notification.repository.NotificationRepository;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import com.erp.global.common.ProductTime;
import com.erp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationDeliveryProperties deliveryProperties;
    private final AccessPolicyService accessPolicyService;

    @Transactional
    public Long create(NotificationCreateRequest request, Long senderId) {
        Member sender = accessPolicyService.getRequester(senderId);
        Member receiver = memberRepository.findById(request.receiverId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        accessPolicyService.validateNotificationReceiverAccess(sender, receiver);

        Notification notification;
        if (request.relatedEntityType() != null && request.relatedEntityId() != null) {
            notification = Notification.createWithRelatedEntity(
                    receiver,
                    request.type(),
                    request.title(),
                    request.content(),
                    request.relatedEntityType(),
                    request.relatedEntityId()
            );
        } else if (request.linkUrl() != null) {
            notification = Notification.createWithLink(
                    receiver,
                    request.type(),
                    request.title(),
                    request.content(),
                    request.linkUrl()
            );
        } else {
            notification = Notification.create(
                    receiver,
                    request.type(),
                    request.title(),
                    request.content()
            );
        }

        Notification saved = notificationRepository.save(notification);
        notificationDispatchService.dispatch(saved);
        return saved.getId();
    }

    /**
     * 간편 알림 생성 메서드 (내부용)
     */
    @Transactional
    public Long notify(Long receiverId, NotificationType type, String title, String content) {
        Member receiver = memberRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Notification notification = Notification.create(receiver, type, title, content);
        Notification saved = notificationRepository.save(notification);
        notificationDispatchService.dispatch(saved);
        return saved.getId();
    }

    /**
     * 링크 포함 알림 생성 (내부용)
     */
    @Transactional
    public Long notifyWithLink(Long receiverId, NotificationType type, String title, String content, String linkUrl) {
        Member receiver = memberRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Notification notification = Notification.createWithLink(receiver, type, title, content, linkUrl);
        Notification saved = notificationRepository.save(notification);
        notificationDispatchService.dispatch(saved);
        return saved.getId();
    }

    /**
     * 링크 포함 알림 생성 (수신자 리스트)
     */
    @Transactional
    public void notifyWithLink(List<Long> receiverIds, NotificationType type, String title, String content, String linkUrl) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }

        int batchSize = resolveBatchSize();
        int total = receiverIds.size();
        for (int start = 0; start < total; start += batchSize) {
            int end = Math.min(start + batchSize, total);
            List<Long> chunk = receiverIds.subList(start, end);
            List<Member> receivers = memberRepository.findAllById(chunk);
            if (receivers.isEmpty()) {
                continue;
            }

            List<Notification> notifications = new java.util.ArrayList<>();
            for (Member receiver : receivers) {
                notifications.add(Notification.createWithLink(receiver, type, title, content, linkUrl));
            }
            List<Notification> saved = notificationRepository.saveAll(notifications);
            notificationDispatchService.dispatch(saved);
        }
    }

    /**
     * 연관 엔티티 포함 알림 생성 (내부용)
     */
    @Transactional
    public Long notifyWithRelatedEntity(Long receiverId, NotificationType type, String title, String content,
                                        String relatedEntityType, Long relatedEntityId) {
        Member receiver = memberRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Notification notification = Notification.createWithRelatedEntity(
                receiver, type, title, content, relatedEntityType, relatedEntityId
        );
        Notification saved = notificationRepository.save(notification);
        notificationDispatchService.dispatch(saved);
        return saved.getId();
    }

    /**
     * 알림 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long receiverId, int limit) {
        int resolvedLimit = resolveLimit(limit, 20);
        List<Notification> notifications = notificationRepository
                .findByReceiverIdAndDeletedAtIsNullOrderByCreatedAtDesc(receiverId, PageRequest.of(0, resolvedLimit));
        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 알림 목록 조회 (타입 필터)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByType(Long receiverId, NotificationType type, int limit) {
        int resolvedLimit = resolveLimit(limit, 20);
        List<Notification> notifications = notificationRepository
                .findByReceiverIdAndTypeAndDeletedAtIsNullOrderByCreatedAtDesc(receiverId, type, PageRequest.of(0, resolvedLimit));
        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 알림 상세 조회
     */
    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long notificationId, Long receiverId) {
        Notification notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getReceiver().getId().equals(receiverId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        return NotificationResponse.from(notification);
    }

    /**
     * 알림 읽음 표시
     */
    @Transactional
    public void markAsRead(Long notificationId, Long receiverId) {
        Notification notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getReceiver().getId().equals(receiverId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markAsRead();
    }

    /**
     * 전체 읽음 표시
     */
    @Transactional
    public void markAllAsRead(Long receiverId) {
        notificationRepository.markAllAsRead(receiverId, ProductTime.nowDateTime());
    }

    /**
     * 알림 삭제 (Soft Delete)
     */
    @Transactional
    public void delete(Long notificationId, Long receiverId) {
        Notification notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getReceiver().getId().equals(receiverId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.softDelete();
    }

    /**
     * 안 읽은 알림 개수
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long receiverId) {
        long count = notificationRepository.countUnreadByReceiverId(receiverId);
        return UnreadCountResponse.of(count);
    }

    /**
     * 안 읽은 알림 목록
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long receiverId) {
        return getUnreadNotifications(receiverId, 50);
    }

    /**
     * 안 읽은 알림 목록 (limit)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long receiverId, int limit) {
        int resolvedLimit = resolveLimit(limit, 50);
        List<Notification> notifications = notificationRepository
                .findByReceiverIdAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(receiverId, PageRequest.of(0, resolvedLimit));
        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 안 읽은 알림 목록 (타입 필터)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotificationsByType(Long receiverId, NotificationType type, int limit) {
        int resolvedLimit = resolveLimit(limit, 50);
        List<Notification> notifications = notificationRepository
                .findByReceiverIdAndTypeAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(receiverId, type, PageRequest.of(0, resolvedLimit));
        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 오래된 읽은 알림 정리 (일괄 작업용)
     */
    @Transactional
    public int cleanupOldReadNotifications(Long receiverId, int daysToKeep) {
        LocalDateTime beforeDate = ProductTime.nowDateTime().minusDays(daysToKeep);
        return notificationRepository.softDeleteOldReadNotifications(receiverId, beforeDate, ProductTime.nowDateTime());
    }

    private int resolveBatchSize() {
        int batchSize = deliveryProperties.getBatchSize();
        return batchSize > 0 ? batchSize : 500;
    }

    private int resolveLimit(int limit, int fallback) {
        if (limit <= 0) {
            return fallback;
        }
        return Math.min(limit, 100);
    }
}
