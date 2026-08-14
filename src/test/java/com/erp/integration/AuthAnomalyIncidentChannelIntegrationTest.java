package com.erp.integration;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.authaudit.service.AuthAnomalyAlertService;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.entity.NotificationOutbox;
import com.erp.domain.notification.repository.NotificationOutboxRepository;
import com.erp.domain.notification.service.NotificationDispatchService;
import com.erp.domain.notification.entity.NotificationChannel;
import com.erp.domain.notification.service.channel.WebhookNotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;

@DisplayName("인증 이상 징후 외부 incident channel 통합 테스트")
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
class AuthAnomalyIncidentChannelIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthAnomalyAlertService authAnomalyAlertService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean(name = "notificationRestTemplate")
    private RestTemplate notificationRestTemplate;

    @BeforeEach
    void resetNotificationRestTemplate() {
        reset(notificationRestTemplate);
    }

    @Test
    @DisplayName("반복 로그인 실패 임계치 도달 시 incident webhook outbox가 적재되고 cooldown 동안 중복 생성되지 않는다")
    void repeatedLoginFailures_QueueSingleIncidentWebhookAlert() {
        given(notificationRestTemplate.postForEntity(
                eq("https://hooks.test/incident"),
                any(HttpEntity.class),
                eq(String.class)
        )).willReturn(ResponseEntity.ok("ok"));

        String targetEmail = writeCommitted(() -> {
            Kindergarten targetKindergarten = testData.createKindergarten();

            var principal = testData.createTestMember(
                    "incident-principal@test.com",
                    "인시던트원장",
                    MemberRole.PRINCIPAL,
                    "test1234"
            );
            principal.assignKindergarten(targetKindergarten);
            memberRepository.save(principal);

            var targetMember = testData.createTestMember(
                    "incident-parent@test.com",
                    "인시던트학부모",
                    MemberRole.PARENT,
                    "test1234"
            );
            targetMember.assignKindergarten(targetKindergarten);
            memberRepository.save(targetMember);

            for (int i = 0; i < 3; i++) {
                authAnomalyAlertService.alertRepeatedLoginFailuresIfNeeded(targetMember.getEmail(), "198.51.100.77");
            }

            return targetMember.getEmail();
        });

        long pendingIncidentCount = readCommitted(() -> notificationOutboxRepository.countByStatusAndChannel(
                NotificationDeliveryStatus.PENDING,
                NotificationChannel.INCIDENT_WEBHOOK
        ));
        assertThat(pendingIncidentCount).isEqualTo(1);

        NotificationOutbox incidentOutbox = readCommitted(
                () -> notificationOutboxRepository.findByChannelOrderByIdAsc(NotificationChannel.INCIDENT_WEBHOOK).get(0)
        );
        assertThat(incidentOutbox.getNotification()).isNotNull();
        assertThat(incidentOutbox.getNotificationType().name()).isEqualTo("AUTH_ANOMALY_DETECTED");
        assertThat(incidentOutbox.getTitle()).contains("인증 이상 징후 감지");
        assertThat(incidentOutbox.getContent()).contains(targetEmail);
        assertThat(incidentOutbox.getLinkUrl()).contains("/audit-logs");

        notificationDispatchService.processReadyDeliveriesBatch();

        long deliveredIncidentCount = readCommitted(() -> notificationOutboxRepository.countByStatusAndChannel(
                NotificationDeliveryStatus.DELIVERED,
                NotificationChannel.INCIDENT_WEBHOOK
        ));
        assertThat(deliveredIncidentCount).isEqualTo(1);

        writeCommitted(() -> {
            for (int i = 0; i < 3; i++) {
                authAnomalyAlertService.alertRepeatedLoginFailuresIfNeeded(targetEmail, "198.51.100.77");
            }
            return null;
        });

        long deliveredIncidentCountAfterCooldown = readCommitted(() -> notificationOutboxRepository.countByStatusAndChannel(
                NotificationDeliveryStatus.DELIVERED,
                NotificationChannel.INCIDENT_WEBHOOK
        ));
        assertThat(deliveredIncidentCountAfterCooldown).isEqualTo(1);

        then(notificationRestTemplate).should(times(1))
                .postForEntity(eq("https://hooks.test/incident"), any(HttpEntity.class), eq(String.class));

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        then(notificationRestTemplate).should().postForEntity(
                eq("https://hooks.test/incident"), requestCaptor.capture(), eq(String.class));
        HttpEntity<?> request = requestCaptor.getValue();
        assertThat(request.getBody()).isInstanceOf(WebhookNotificationPayload.class);
        assertSignedWebhookRequest(request);
    }

    private void assertSignedWebhookRequest(HttpEntity<?> request) {
        HttpHeaders headers = request.getHeaders();
        String timestamp = headers.getFirst("X-Webhook-Timestamp");
        String signature = headers.getFirst("X-Webhook-Signature");
        assertThat(timestamp).isNotBlank();
        assertThat(signature).startsWith("v1=");

        try {
            String serializedBody = objectMapper.writeValueAsString(request.getBody());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-incident-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(
                    (timestamp + "." + serializedBody).getBytes(StandardCharsets.UTF_8)));
            assertThat(signature).isEqualTo("v1=" + expected);
            assertThat(Long.parseLong(timestamp)).isBetween(
                    Instant.now().minusSeconds(10).getEpochSecond(),
                    Instant.now().plusSeconds(10).getEpochSecond());
        } catch (JsonProcessingException | NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new AssertionError("webhook signature contract could not be verified", exception);
        }
    }

}
