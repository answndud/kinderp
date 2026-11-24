package com.kinderp.domain.notification.service;

import com.kinderp.domain.notification.config.NotificationDeliveryProperties;
import com.kinderp.domain.notification.entity.Notification;
import com.kinderp.domain.notification.service.channel.NotificationDeliveryPayload;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.repository.NotificationOutboxRepository;
import com.kinderp.domain.notification.entity.NotificationChannel;
import com.kinderp.domain.notification.service.channel.NotificationChannelSender;
import com.kinderp.domain.notification.service.channel.NotificationChannelSenderRegistry;
import com.kinderp.global.common.ProductTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDispatchService {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationDeliveryPolicyService notificationDeliveryPolicyService;
    private final NotificationDeliveryProperties deliveryProperties;
    private final NotificationChannelSenderRegistry senderRegistry;
    private final PlatformTransactionManager transactionManager;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void dispatch(Notification notification) {
        if (notification == null) {
            return;
        }

        enqueueNotifications(List.of(notification));
    }

    @Transactional
    public void dispatch(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        enqueueNotifications(notifications);
    }

    @Scheduled(fixedDelayString = "${notification.delivery.worker-fixed-delay-ms:15000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processReadyDeliveriesOnSchedule() {
        if (!deliveryProperties.isWorkerEnabled()) {
            return;
        }
        processReadyDeliveriesBatch();
    }

    /**
     * 바깥 트랜잭션 없이 claim → 발송 → 결과 기록을 각각 짧은 트랜잭션으로 나눠 실행한다.
     * 외부 HTTP 호출이 DB 커넥션을 붙잡지 않도록 발송은 트랜잭션 밖에서 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int processReadyDeliveriesBatch() {
        Timer.Sample timer = Timer.start(meterRegistry);
        LocalDateTime now = ProductTime.nowDateTime();
        try {
            List<ClaimedDelivery> claimed = executeInNewTransaction(() -> claimReadyDeliveries(now));
            claimed.forEach(this::processClaimedDelivery);
            meterRegistry.summary("notification.delivery.batch.claimed").record(claimed.size());
            return claimed.size();
        } finally {
            timer.stop(meterRegistry.timer("notification.delivery.batch.duration"));
        }
    }

    private List<ClaimedDelivery> claimReadyDeliveries(LocalDateTime now) {
        int batchSize = resolveWorkerBatchSize();
        LocalDateTime staleBefore = now.minus(resolveProcessingTimeout());

        List<Long> claimedIds = new ArrayList<>(notificationOutboxRepository.claimPendingIds(now, batchSize));
        // PENDING이 배치를 못 채우면 남은 자리만큼 timeout된 PROCESSING을 회수한다 (부하가 계속돼도 굶지 않는다).
        int remaining = batchSize - claimedIds.size();
        if (remaining > 0) {
            claimedIds.addAll(notificationOutboxRepository.claimStaleProcessingIds(staleBefore, remaining));
        }
        if (claimedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, NotificationOutbox> outboxById = new HashMap<>();
        notificationOutboxRepository.findByIdIn(claimedIds)
                .forEach(outbox -> outboxById.put(outbox.getId(), outbox));

        List<ClaimedDelivery> claimed = new ArrayList<>(claimedIds.size());
        for (Long id : claimedIds) {
            NotificationOutbox outbox = outboxById.get(id);
            if (outbox == null) {
                continue;
            }
            String claimToken = UUID.randomUUID().toString();
            outbox.markProcessing(now, claimToken);
            claimed.add(new ClaimedDelivery(id, claimToken));
        }
        notificationOutboxRepository.flush();
        return claimed;
    }

    private void processClaimedDelivery(ClaimedDelivery claimed) {
        PreparedDelivery prepared = executeInNewTransaction(() -> prepareDelivery(claimed));
        if (prepared == null) {
            return;
        }

        NotificationChannelSender sender = resolveSender(prepared.channel());
        try {
            sender.send(prepared.payload());
        } catch (Exception ex) {
            recordFailure(claimed, prepared, ex);
            return;
        }

        LocalDateTime now = ProductTime.nowDateTime();
        int updated = executeInNewTransaction(
                () -> notificationOutboxRepository.markDeliveredIfClaimed(claimed.id(), claimed.claimToken(), now));
        if (updated == 0) {
            recordFenced(claimed, prepared);
            return;
        }
        recordDelivery(prepared.channel(), "delivered");
        log.debug("Notification outbox delivered. outboxId={}, channel={}, notificationId={}",
                claimed.id(), prepared.channel(), prepared.payload().notificationId());
    }

    /**
     * 내 claim이 아직 유효한지 확인하고 발송에 필요한 값만 꺼낸다. lazy 연관은 이 트랜잭션 안에서만 접근한다.
     */
    private PreparedDelivery prepareDelivery(ClaimedDelivery claimed) {
        NotificationOutbox outbox = notificationOutboxRepository.findById(claimed.id()).orElse(null);
        if (outbox == null || outbox.getChannel() == null) {
            return null;
        }
        if (outbox.getStatus() != NotificationDeliveryStatus.PROCESSING
                || !claimed.claimToken().equals(outbox.getClaimToken())) {
            log.info("Notification outbox claim lost before send. outboxId={}", claimed.id());
            return null;
        }
        return new PreparedDelivery(
                outbox.getChannel(),
                outbox.toPayload(),
                outbox.getAttemptCount(),
                outbox.canRetry()
        );
    }

    private void recordFailure(ClaimedDelivery claimed, PreparedDelivery prepared, Exception ex) {
        LocalDateTime now = ProductTime.nowDateTime();
        String error = NotificationOutbox.truncateError(ex.getMessage());

        if (!prepared.canRetry()) {
            int updated = executeInNewTransaction(
                    () -> notificationOutboxRepository.markDeadLetterIfClaimed(
                            claimed.id(), claimed.claimToken(), now, error));
            if (updated == 0) {
                recordFenced(claimed, prepared);
                return;
            }
            recordDelivery(prepared.channel(), "dead_letter");
            log.warn("Notification outbox dead-lettered. outboxId={}, channel={}, attempts={}, error={}",
                    claimed.id(), prepared.channel(), prepared.attemptCount(), ex.getMessage());
            return;
        }

        Duration retryDelay = resolveRetryDelay(prepared.attemptCount());
        int updated = executeInNewTransaction(
                () -> notificationOutboxRepository.scheduleRetryIfClaimed(
                        claimed.id(), claimed.claimToken(), now.plus(retryDelay), error));
        if (updated == 0) {
            recordFenced(claimed, prepared);
            return;
        }
        recordDelivery(prepared.channel(), "retry_scheduled");
        log.warn("Notification outbox rescheduled. outboxId={}, channel={}, attempts={}, retryAfterMs={}, error={}",
                claimed.id(), prepared.channel(), prepared.attemptCount(), retryDelay.toMillis(), ex.getMessage());
    }

    /**
     * 발송은 했지만 그 사이 다른 worker가 같은 건을 재claim했다. 결과는 버리고 재claim한 쪽이 최종 상태를 정한다.
     * 이 경로에서는 같은 알림이 두 번 나갈 수 있으므로 수신 측은 notificationId로 멱등 처리해야 한다 (at-least-once).
     */
    private void recordFenced(ClaimedDelivery claimed, PreparedDelivery prepared) {
        recordDelivery(prepared.channel(), "fenced");
        log.info("Notification outbox result discarded because the claim was taken over. outboxId={}, channel={}",
                claimed.id(), prepared.channel());
    }

    private void recordDelivery(NotificationChannel channel, String outcome) {
        meterRegistry.counter("notification.delivery.attempts",
                        "channel", channel.name(),
                        "outcome", outcome)
                .increment();
    }

    private record ClaimedDelivery(Long id, String claimToken) {
    }

    private record PreparedDelivery(NotificationChannel channel,
                                    NotificationDeliveryPayload payload,
                                    int attemptCount,
                                    boolean canRetry) {
    }

    private void enqueueNotifications(List<Notification> notifications) {
        List<NotificationOutbox> outboxes = notifications.stream()
                .filter(notification -> notification != null && notification.getId() != null)
                .flatMap(notification -> notificationDeliveryPolicyService.resolveChannels(notification).stream()
                        .map(channel -> NotificationOutbox.create(notification, channel, resolveMaxAttempts())))
                .toList();

        if (outboxes.isEmpty()) {
            return;
        }

        notificationOutboxRepository.saveAll(outboxes);
    }

    private NotificationChannelSender resolveSender(NotificationChannel channel) {
        return senderRegistry.getRequired(channel);
    }

    private int resolveWorkerBatchSize() {
        return Math.max(deliveryProperties.getWorkerBatchSize(), 1);
    }

    private int resolveMaxAttempts() {
        return Math.max(deliveryProperties.getMaxAttempts(), 1);
    }

    private Duration resolveProcessingTimeout() {
        Duration timeout = deliveryProperties.getProcessingTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return Duration.ofMinutes(5);
        }
        return timeout;
    }

    private Duration resolveRetryDelay(int attemptCount) {
        Duration initialDelay = deliveryProperties.getInitialRetryDelay();
        Duration maxDelay = deliveryProperties.getMaxRetryDelay();
        long baseDelayMs = initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()
                ? Duration.ofSeconds(30).toMillis()
                : initialDelay.toMillis();
        long cappedMaxDelayMs = maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()
                ? Duration.ofMinutes(15).toMillis()
                : maxDelay.toMillis();
        double multiplier = deliveryProperties.getRetryBackoffMultiplier();
        if (multiplier < 1.0d) {
            multiplier = 1.0d;
        }

        long delayMs = Math.round(baseDelayMs * Math.pow(multiplier, Math.max(attemptCount - 1, 0)));
        delayMs = Math.min(delayMs, cappedMaxDelayMs);
        return Duration.ofMillis(delayMs);
    }

    private <T> T executeInNewTransaction(Supplier<T> supplier) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> supplier.get());
    }
}
