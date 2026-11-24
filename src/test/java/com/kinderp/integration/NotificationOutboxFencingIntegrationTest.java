package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.repository.NotificationOutboxRepository;
import com.kinderp.domain.notification.service.NotificationDispatchService;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.global.common.ProductTime;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

@DisplayName("알림 outbox claim token fencing 통합 테스트")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "notification.delivery.worker-enabled=false",
        "notification.delivery.worker-batch-size=2",
        "notification.delivery.app.enabled=true",
        "notification.delivery.app.webhook-url=https://hooks.test/app",
        "notification.delivery.external-types[0]=SYSTEM",
        "notification.delivery.max-attempts=3",
        "notification.delivery.initial-retry-delay=1s",
        "notification.delivery.max-retry-delay=1s",
        "notification.delivery.retry-backoff-multiplier=1.0"
})
@Tag("integration")
class NotificationOutboxFencingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @MockitoBean(name = "notificationRestTemplate")
    private RestTemplate notificationRestTemplate;

    @BeforeEach
    void resetNotificationRestTemplate() {
        reset(notificationRestTemplate);
    }

    @Test
    @DisplayName("재claim된 outbox에는 이전 claim token의 결과가 반영되지 않는다")
    void staleClaimToken_ResultIsDiscarded() {
        Long outboxId = createOutbox("fencing-repo@test.com");
        LocalDateTime now = ProductTime.nowDateTime();

        writeCommitted(() -> {
            NotificationOutbox outbox = notificationOutboxRepository.findById(outboxId).orElseThrow();
            outbox.markProcessing(now, "token-A");
            return notificationOutboxRepository.save(outbox);
        });
        // timeout 뒤 다른 worker가 재claim한 상황
        writeCommitted(() -> {
            NotificationOutbox outbox = notificationOutboxRepository.findById(outboxId).orElseThrow();
            outbox.markProcessing(now, "token-B");
            return notificationOutboxRepository.save(outbox);
        });

        assertThat(writeCommitted(() -> notificationOutboxRepository.markDeliveredIfClaimed(outboxId, "token-A", now))).isZero();
        assertThat(writeCommitted(() -> notificationOutboxRepository.scheduleRetryIfClaimed(outboxId, "token-A", now, "late"))).isZero();
        assertThat(writeCommitted(() -> notificationOutboxRepository.markDeadLetterIfClaimed(outboxId, "token-A", now, "late"))).isZero();

        NotificationOutbox untouched = readCommitted(() -> notificationOutboxRepository.findById(outboxId).orElseThrow());
        assertThat(untouched.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
        assertThat(untouched.getClaimToken()).isEqualTo("token-B");

        assertThat(writeCommitted(() -> notificationOutboxRepository.markDeliveredIfClaimed(outboxId, "token-B", now))).isEqualTo(1);

        NotificationOutbox delivered = readCommitted(() -> notificationOutboxRepository.findById(outboxId).orElseThrow());
        assertThat(delivered.getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(delivered.getClaimToken()).isNull();
        assertThat(delivered.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("발송이 timeout을 넘겨 다른 worker가 가져가면, 늦게 끝난 worker의 실패가 최종 상태를 덮어쓰지 않는다")
    void slowWorker_DoesNotOverwriteTakeover() throws Exception {
        Long outboxId = createOutbox("fencing-takeover@test.com");

        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSend = new CountDownLatch(1);
        AtomicInteger sendCalls = new AtomicInteger();

        given(notificationRestTemplate.postForEntity(anyString(), any(HttpEntity.class), any()))
                .willAnswer(invocation -> {
                    if (sendCalls.incrementAndGet() == 1) {
                        firstSendStarted.countDown();
                        assertThat(releaseFirstSend.await(10, TimeUnit.SECONDS)).isTrue();
                        throw new IllegalStateException("late failure from the first worker");
                    }
                    return ResponseEntity.ok("ok");
                });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> slowWorker = executor.submit(notificationDispatchService::processReadyDeliveriesBatch);
            assertThat(firstSendStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // 첫 worker의 발송이 오래 걸려 processing-timeout이 지난 것으로 만든다.
            writeCommitted(() -> jdbcTemplate.update(
                    "UPDATE notification_outbox SET processing_started_at = ? WHERE id = ?",
                    ProductTime.nowDateTime().minusHours(1), outboxId));

            assertThat(notificationDispatchService.processReadyDeliveriesBatch()).isEqualTo(1);

            releaseFirstSend.countDown();
            assertThat(slowWorker.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        NotificationOutbox outbox = readCommitted(() -> notificationOutboxRepository.findById(outboxId).orElseThrow());
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getAttemptCount()).isEqualTo(2);
        then(notificationRestTemplate).should(times(2)).postForEntity(anyString(), any(HttpEntity.class), any());
    }

    @Test
    @DisplayName("PENDING이 배치를 채우지 못하면 남은 자리만큼 timeout된 PROCESSING도 같은 배치에서 회수한다")
    void staleProcessing_IsReclaimedWhenBatchHasRoom() {
        Long pendingId = createOutbox("fencing-pending@test.com");
        Long staleId = createOutbox("fencing-stale@test.com");

        writeCommitted(() -> jdbcTemplate.update(
                "UPDATE notification_outbox SET status = 'PROCESSING', claim_token = 'old-token', "
                        + "attempt_count = 1, processing_started_at = ? WHERE id = ?",
                ProductTime.nowDateTime().minusHours(1), staleId));

        given(notificationRestTemplate.postForEntity(anyString(), any(HttpEntity.class), any()))
                .willReturn(ResponseEntity.ok("ok"));

        assertThat(notificationDispatchService.processReadyDeliveriesBatch()).isEqualTo(2);

        assertThat(readCommitted(() -> notificationOutboxRepository.findById(pendingId).orElseThrow()).getStatus())
                .isEqualTo(NotificationDeliveryStatus.DELIVERED);
        NotificationOutbox reclaimed = readCommitted(() -> notificationOutboxRepository.findById(staleId).orElseThrow());
        assertThat(reclaimed.getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
    }

    private Long createOutbox(String receiverEmail) {
        return writeCommitted(() -> {
            var receiver = testData.createTestMember(receiverEmail, "fencing수신자", MemberRole.PARENT, "test1234");
            Long notificationId = notificationService.notifyWithLink(
                    receiver.getId(),
                    NotificationType.SYSTEM,
                    "outbox fencing",
                    "claim token 테스트",
                    "/notifications"
            );
            return notificationOutboxRepository.findByNotificationIdOrderByIdAsc(notificationId).get(0).getId();
        });
    }
}
