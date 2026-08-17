package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.repository.NotificationOutboxRepository;
import com.kinderp.domain.notification.service.NotificationDispatchService;
import com.kinderp.domain.notification.service.NotificationService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@DisplayName("알림 outbox retry 통합 테스트")
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
class NotificationOutboxRetryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @MockitoBean(name = "notificationRestTemplate")
    private RestTemplate notificationRestTemplate;

    @Test
    @DisplayName("외부 채널 전달 실패가 반복되면 outbox는 DEAD_LETTER로 전이한다")
    void failedDelivery_IsRetriedAndDeadLettered() {
        given(notificationRestTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                any()
        )).willThrow(new IllegalStateException("app webhook down"));

        Long notificationId = writeCommitted(() -> {
            var receiver = testData.createTestMember(
                    "outbox-deadletter-receiver@test.com",
                    "데드레터수신자",
                    MemberRole.PARENT,
                    "test1234"
            );
            return notificationService.notifyWithLink(
                    receiver.getId(),
                    NotificationType.SYSTEM,
                    "outbox 실패",
                    "재시도 테스트",
                    "/notifications"
            );
        });

        notificationDispatchService.processReadyDeliveriesBatch();

        NotificationOutbox firstAttempt = readCommitted(
                () -> notificationOutboxRepository.findByNotificationIdOrderByIdAsc(notificationId).get(0)
        );
        assertThat(firstAttempt.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(firstAttempt.getLastError()).contains("app webhook down");

        writeCommitted(() -> {
            jdbcTemplate.update(
                    "UPDATE notification_outbox SET next_attempt_at = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)),
                    firstAttempt.getId()
            );
            return null;
        });

        notificationDispatchService.processReadyDeliveriesBatch();

        NotificationOutbox finalAttempt = readCommitted(
                () -> notificationOutboxRepository.findByNotificationIdOrderByIdAsc(notificationId).get(0)
        );
        assertThat(finalAttempt.getStatus()).isEqualTo(NotificationDeliveryStatus.DEAD_LETTER);
        assertThat(finalAttempt.getDeadLetteredAt()).isNotNull();

        then(notificationRestTemplate).should(times(2))
                .postForEntity(anyString(), any(HttpEntity.class), any());
    }
}
