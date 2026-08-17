package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.authaudit.service.AuthAnomalyAlertService;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.service.NotificationDispatchService;
import com.kinderp.domain.notification.repository.NotificationOutboxRepository;
import com.kinderp.domain.notification.entity.NotificationChannel;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@DisplayName("인증 이상 징후 incident retry 통합 테스트")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "notification.delivery.worker-enabled=false",
        "notification.delivery.incident-webhook.enabled=true",
        "notification.delivery.incident-webhook.webhook-url=https://hooks.test/incident",
        "notification.delivery.incident-types[0]=AUTH_ANOMALY_DETECTED",
        "notification.delivery.max-attempts=2",
        "notification.delivery.initial-retry-delay=1s",
        "notification.delivery.max-retry-delay=1s",
        "notification.delivery.retry-backoff-multiplier=1.0"
})
@Tag("integration")
class AuthAnomalyIncidentChannelRetryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthAnomalyAlertService authAnomalyAlertService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @MockitoBean(name = "notificationRestTemplate")
    private RestTemplate notificationRestTemplate;

    @Test
    @DisplayName("incident webhook 전달 실패가 반복되면 outbox는 DEAD_LETTER로 전이한다")
    void incidentWebhookFailure_IsRetriedAndDeadLettered() {
        given(notificationRestTemplate.postForEntity(
                eq("https://hooks.test/incident"),
                any(HttpEntity.class),
                eq(String.class)
        )).willThrow(new IllegalStateException("incident webhook down"));

        writeCommitted(() -> {
            Kindergarten targetKindergarten = testData.createKindergarten();

            var principal = testData.createTestMember(
                    "incident-failure-principal@test.com",
                    "인시던트실패원장",
                    MemberRole.PRINCIPAL,
                    "test1234"
            );
            principal.assignKindergarten(targetKindergarten);
            memberRepository.save(principal);

            var targetMember = testData.createTestMember(
                    "incident-failure-parent@test.com",
                    "인시던트실패학부모",
                    MemberRole.PARENT,
                    "test1234"
            );
            targetMember.assignKindergarten(targetKindergarten);
            memberRepository.save(targetMember);

            for (int i = 0; i < 3; i++) {
                authAnomalyAlertService.alertRepeatedLoginFailuresIfNeeded(targetMember.getEmail(), "198.51.100.78");
            }
            return null;
        });

        notificationDispatchService.processReadyDeliveriesBatch();

        NotificationOutbox firstAttempt = readCommitted(
                () -> notificationOutboxRepository.findByChannelOrderByIdAsc(NotificationChannel.INCIDENT_WEBHOOK).get(0)
        );
        assertThat(firstAttempt.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(firstAttempt.getLastError()).contains("incident webhook down");

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
                () -> notificationOutboxRepository.findByChannelOrderByIdAsc(NotificationChannel.INCIDENT_WEBHOOK).get(0)
        );
        assertThat(finalAttempt.getStatus()).isEqualTo(NotificationDeliveryStatus.DEAD_LETTER);
        assertThat(finalAttempt.getDeadLetteredAt()).isNotNull();

        then(notificationRestTemplate).should(times(2))
                .postForEntity(eq("https://hooks.test/incident"), any(HttpEntity.class), eq(String.class));
    }
}
