package com.erp.performance;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.notification.entity.Notification;
import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.entity.NotificationOutbox;
import com.erp.domain.notification.entity.NotificationType;
import com.erp.domain.notification.repository.NotificationOutboxRepository;
import com.erp.domain.notification.repository.NotificationRepository;
import com.erp.domain.notification.service.NotificationOutboxOpsService;
import com.erp.domain.notification.entity.NotificationChannel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("성능 스모크 - 알림 Outbox 운영 콘솔")
@Tag("performance")
class NotificationOutboxPerformanceSmokeTest extends BaseIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private NotificationOutboxOpsService notificationOutboxOpsService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Outbox timeline 상태/채널 필터는 예상 쿼리 예산 안에 들어온다")
    void outboxTimeline_StaysWithinQueryBudget() {
        seedOutboxTimeline(80);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        Measurement statusChannel = readCommitted(() -> measure(statistics, () -> notificationOutboxOpsService.getTimeline(
                principalMember.getKindergarten().getId(),
                0,
                20,
                NotificationDeliveryStatus.DEAD_LETTER,
                NotificationChannel.EMAIL,
                null
        )));
        Measurement keyword = readCommitted(() -> measure(statistics, () -> notificationOutboxOpsService.getTimeline(
                principalMember.getKindergarten().getId(),
                0,
                20,
                NotificationDeliveryStatus.DEAD_LETTER,
                NotificationChannel.EMAIL,
                "smtp"
        )));

        System.out.printf("[PERF] outbox-timeline-status-channel - queries=%d, elapsedMs=%d%n",
                statusChannel.queryCount, statusChannel.elapsedMs);
        System.out.printf("[PERF] outbox-timeline-keyword        - queries=%d, elapsedMs=%d%n",
                keyword.queryCount, keyword.elapsedMs);

        assertTrue(statusChannel.queryCount <= 2, "outbox status/channel timeline should stay within page + count budget");
        assertTrue(keyword.queryCount <= 2, "outbox keyword timeline should stay within page + count budget");
    }

    @Test
    @DisplayName("대규모 Outbox fixture에서도 timeline 쿼리 예산은 일정하다")
    void outboxTimeline_StaysWithinQueryBudgetForLargeFixture() {
        seedOutboxTimeline(1_000);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        Measurement largeTimeline = readCommitted(() -> measure(statistics, () -> notificationOutboxOpsService.getTimeline(
                principalMember.getKindergarten().getId(),
                0,
                20,
                NotificationDeliveryStatus.DEAD_LETTER,
                NotificationChannel.EMAIL,
                null
        )));

        System.out.printf("[PERF] outbox-large-fixture - rows=%d, queries=%d, elapsedMs=%d%n",
                1_000,
                largeTimeline.queryCount,
                largeTimeline.elapsedMs);

        assertTrue(largeTimeline.queryCount <= 2,
                "outbox timeline should keep a fixed page + count query budget for a large fixture");
    }

    private void seedOutboxTimeline(int bulkCount) {
        writeCommitted(() -> {
            String token = Long.toString(System.nanoTime());
            Member receiver = testData.createTestMember(
                    "outbox-perf-parent-" + token + "@test.com",
                    "Outbox성능학부모" + token,
                    MemberRole.PARENT,
                    "test1234"
            );
            memberRepository.save(receiver);
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < bulkCount; i++) {
                Notification notification = notificationRepository.save(Notification.createWithLink(
                        receiver,
                        NotificationType.SYSTEM,
                        "Outbox timeline smoke " + i,
                        "운영 검색 성능 스모크 " + i,
                        "/notification-outbox"
                ));
                NotificationChannel channel = i % 2 == 0 ? NotificationChannel.EMAIL : NotificationChannel.APP;
                NotificationOutbox outbox = NotificationOutbox.create(notification, channel, 2);
                outbox.markProcessing(now.minusMinutes(i + 1L));
                if (i % 3 == 0) {
                    outbox.markDeadLetter(now.minusMinutes(i), "smtp transient failure " + i);
                } else {
                    outbox.markDelivered(now.minusMinutes(i));
                }
                notificationOutboxRepository.save(outbox);
            }
            notificationOutboxRepository.flush();
            entityManager.clear();
            return null;
        });
    }

    private Measurement measure(Statistics statistics, Runnable action) {
        entityManager.clear();
        statistics.clear();
        long start = System.nanoTime();
        action.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long queryCount = statistics.getPrepareStatementCount();
        return new Measurement(queryCount, elapsedMs);
    }

    private record Measurement(long queryCount, long elapsedMs) {
    }
}
