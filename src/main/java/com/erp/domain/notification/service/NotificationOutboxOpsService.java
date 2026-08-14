package com.erp.domain.notification.service;

import com.erp.domain.notification.config.NotificationDeliveryProperties;
import com.erp.domain.notification.dto.response.NotificationOutboxItemResponse;
import com.erp.domain.notification.dto.response.NotificationOutboxSummaryResponse;
import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.entity.NotificationOutbox;
import com.erp.domain.notification.repository.NotificationOutboxRepository;
import com.erp.domain.notification.entity.NotificationChannel;
import com.erp.domain.domainaudit.entity.DomainAuditAction;
import com.erp.domain.domainaudit.entity.DomainAuditTargetType;
import com.erp.domain.domainaudit.service.DomainAuditLogService;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberRole;
import com.erp.global.security.access.AccessPolicyService;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationOutboxOpsService {

    private static final int MAX_DEAD_LETTER_LIMIT = 100;
    private static final int MAX_TIMELINE_LIMIT = 100;

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationDeliveryProperties deliveryProperties;
    private final AccessPolicyService accessPolicyService;
    private final DomainAuditLogService domainAuditLogService;

    public NotificationOutboxSummaryResponse getSummary(Long kindergartenId) {
        Map<String, Long> statusCounts = Arrays.stream(NotificationDeliveryStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> notificationOutboxRepository
                                .countByNotificationReceiverKindergartenIdAndStatus(kindergartenId, status)
                ));
        Map<String, Long> deadLetterCountsByChannel = Arrays.stream(NotificationChannel.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        channel -> notificationOutboxRepository.countByNotificationReceiverKindergartenIdAndStatusAndChannel(
                                kindergartenId,
                                NotificationDeliveryStatus.DEAD_LETTER,
                                channel
                        )
                ));

        return new NotificationOutboxSummaryResponse(statusCounts, deadLetterCountsByChannel);
    }

    public Page<NotificationOutboxItemResponse> getTimeline(
            Long kindergartenId,
            int page,
            int size,
            NotificationDeliveryStatus status,
            NotificationChannel channel,
            String keyword
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_TIMELINE_LIMIT);
        PageRequest pageRequest = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return notificationOutboxRepository.searchTimeline(
                        kindergartenId,
                        status,
                        channel,
                        normalizeKeyword(keyword),
                        pageRequest
                )
                .map(NotificationOutboxItemResponse::from);
    }

    public Page<NotificationOutboxItemResponse> getDeadLetters(
            Long kindergartenId,
            int page,
            int size,
            NotificationChannel channel
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_DEAD_LETTER_LIMIT);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize);
        if (channel != null) {
            return notificationOutboxRepository.findByNotificationReceiverKindergartenIdAndStatusAndChannelOrderByDeadLetteredAtDescIdDesc(
                            kindergartenId,
                            NotificationDeliveryStatus.DEAD_LETTER,
                            channel,
                            pageRequest
                    )
                    .map(NotificationOutboxItemResponse::from);
        }

        return notificationOutboxRepository.findByNotificationReceiverKindergartenIdAndStatusOrderByDeadLetteredAtDescIdDesc(
                        kindergartenId,
                        NotificationDeliveryStatus.DEAD_LETTER,
                        pageRequest
                )
                .map(NotificationOutboxItemResponse::from);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase();
    }

    @Transactional
    public NotificationOutboxItemResponse retryDeadLetter(Long kindergartenId, Long outboxId, Long reviewerId) {
        Member reviewer = accessPolicyService.getRequester(reviewerId);
        if (reviewer.getRole() != MemberRole.PRINCIPAL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        accessPolicyService.validateSameKindergarten(reviewer, kindergartenId);

        NotificationOutbox outbox = notificationOutboxRepository
                .findByIdAndNotificationReceiverKindergartenId(outboxId, kindergartenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (outbox.getStatus() != NotificationDeliveryStatus.DEAD_LETTER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "dead-letter 상태의 outbox만 재시도할 수 있습니다");
        }

        outbox.resetDeadLetterForRetry(LocalDateTime.now(), deliveryProperties.getMaxAttempts());
        domainAuditLogService.record(
                reviewer,
                kindergartenId,
                DomainAuditAction.NOTIFICATION_OUTBOX_RETRIED,
                DomainAuditTargetType.NOTIFICATION_OUTBOX,
                outbox.getId(),
                reviewer.getName() + "이(가) 알림 outbox를 재시도했습니다.",
                Map.of(
                        "channel", outbox.getChannel().name(),
                        "attemptCount", outbox.getAttemptCount()
                )
        );
        return NotificationOutboxItemResponse.from(outbox);
    }
}
