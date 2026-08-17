package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.repository.NotificationOutboxRepository;
import com.kinderp.domain.notification.service.NotificationDispatchService;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.domain.notification.entity.NotificationChannel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;

@DisplayName("알림 outbox 통합 테스트")
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
class NotificationOutboxIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("SYSTEM 알림은 outbox에 적재되고 외부 채널 전달 성공 시 DELIVERED로 전이한다")
    void systemNotification_IsQueuedAndDelivered() {
        given(notificationRestTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                any()
        )).willReturn(ResponseEntity.ok("ok"));

        Long notificationId = writeCommitted(() -> {
            var receiver = testData.createTestMember(
                    "outbox-delivery-receiver@test.com",
                    "아웃박스수신자",
                    MemberRole.PARENT,
                    "test1234"
            );
            return notificationService.notifyWithLink(
                    receiver.getId(),
                    NotificationType.SYSTEM,
                    "outbox 성공",
                    "외부 채널 전달 테스트",
                    "/notifications"
            );
        });

        List<NotificationOutbox> queuedEntries = readCommitted(
                () -> notificationOutboxRepository.findByNotificationIdOrderByIdAsc(notificationId)
        );
        assertThat(queuedEntries).hasSize(1);
        assertThat(queuedEntries.get(0).getChannel()).isEqualTo(NotificationChannel.APP);
        assertThat(queuedEntries.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);

        notificationDispatchService.processReadyDeliveriesBatch();

        List<NotificationOutbox> deliveredEntries = readCommitted(
                () -> notificationOutboxRepository.findByNotificationIdOrderByIdAsc(notificationId)
        );
        assertThat(deliveredEntries).hasSize(1);
        assertThat(deliveredEntries.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(deliveredEntries.get(0).getDeliveredAt()).isNotNull();

        then(notificationRestTemplate).should(times(1))
                .postForEntity(anyString(), any(HttpEntity.class), any());
    }

}
