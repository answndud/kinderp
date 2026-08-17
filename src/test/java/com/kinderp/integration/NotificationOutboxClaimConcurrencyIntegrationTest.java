package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.repository.NotificationOutboxRepository;
import com.kinderp.domain.notification.service.NotificationDispatchService;
import com.kinderp.domain.notification.service.NotificationService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

@DisplayName("알림 outbox atomic claim 통합 테스트")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "notification.delivery.worker-enabled=false",
        "notification.delivery.app.enabled=true",
        "notification.delivery.app.webhook-url=https://hooks.test/app",
        "notification.delivery.external-types[0]=SYSTEM",
        "notification.delivery.max-attempts=2",
        "notification.delivery.initial-retry-delay=1s",
        "notification.delivery.max-retry-delay=1s",
        "notification.delivery.retry-backoff-multiplier=1.0"
})
@Tag("integration")
class NotificationOutboxClaimConcurrencyIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("동시에 두 worker가 배치를 처리해도 같은 outbox는 한 번만 claim된다")
    void concurrentWorkers_ClaimSameOutboxOnlyOnce() throws Exception {
        CountDownLatch deliveryStartedLatch = new CountDownLatch(1);
        CountDownLatch releaseDeliveryLatch = new CountDownLatch(1);

        given(notificationRestTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                any()
        )).willAnswer(invocation -> {
            deliveryStartedLatch.countDown();
            assertThat(releaseDeliveryLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return ResponseEntity.ok("ok");
        });

        Long notificationId = writeCommitted(() -> {
            var receiver = testData.createTestMember(
                    "outbox-concurrency-receiver@test.com",
                    "동시성수신자",
                    MemberRole.PARENT,
                    "test1234"
            );
            return notificationService.notifyWithLink(
                    receiver.getId(),
                    NotificationType.SYSTEM,
                    "outbox 동시성",
                    "atomic claim 테스트",
                    "/notifications"
            );
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Integer> firstWorker = executor.submit(() -> {
                readyLatch.countDown();
                assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
                return notificationDispatchService.processReadyDeliveriesBatch();
            });
            Future<Integer> secondWorker = executor.submit(() -> {
                readyLatch.countDown();
                assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
                return notificationDispatchService.processReadyDeliveriesBatch();
            });

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(deliveryStartedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            releaseDeliveryLatch.countDown();

            List<Integer> claimedCounts = List.of(
                    firstWorker.get(10, TimeUnit.SECONDS),
                    secondWorker.get(10, TimeUnit.SECONDS)
            );

            assertThat(claimedCounts).containsExactlyInAnyOrder(1, 0);

            NotificationOutbox delivered = readCommitted(
                    () -> notificationOutboxRepository.findByNotificationIdOrderByIdAsc(notificationId).get(0)
            );
            assertThat(delivered.getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
            assertThat(delivered.getDeliveredAt()).isNotNull();

            then(notificationRestTemplate).should(times(1))
                    .postForEntity(anyString(), any(HttpEntity.class), any());
        } finally {
            executor.shutdownNow();
        }
    }
}
