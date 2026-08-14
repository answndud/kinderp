package com.erp.api;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.member.entity.Member;
import com.erp.domain.notification.entity.Notification;
import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.entity.NotificationOutbox;
import com.erp.domain.notification.entity.NotificationType;
import com.erp.domain.notification.repository.NotificationOutboxRepository;
import com.erp.domain.notification.repository.NotificationRepository;
import com.erp.domain.notification.entity.NotificationChannel;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.kindergarten.entity.Kindergarten;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("알림 outbox 운영 API 테스트")
@Tag("integration")
class NotificationOutboxOpsApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Test
    @DisplayName("원장은 dead-letter summary와 목록을 조회할 수 있다")
    void principalCanReadDeadLetterOps() throws Exception {
        Long outboxId = createDeadLetterOutbox();

        mockMvc.perform(get("/api/v1/notification-outbox/summary")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statusCounts.DEAD_LETTER").value(1))
                .andExpect(jsonPath("$.data.deadLetterCountsByChannel.APP").value(1));

        mockMvc.perform(get("/api/v1/notification-outbox/dead-letters")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(outboxId))
                .andExpect(jsonPath("$.data.content[0].status").value("DEAD_LETTER"));
    }

    @Test
    @DisplayName("원장은 다른 유치원의 outbox를 조회하거나 재시도할 수 없다")
    void principalCannotReadOtherKindergartenOutbox() throws Exception {
        Kindergarten otherKindergarten = testData.createKindergarten();
        Member otherPrincipal = createMemberInKindergarten(
                "other-outbox-principal@test.com",
                "다른 유치원 원장",
                MemberRole.PRINCIPAL,
                otherKindergarten
        );
        Member otherParent = createMemberInKindergarten(
                "other-outbox-parent@test.com",
                "다른 유치원 학부모",
                MemberRole.PARENT,
                otherKindergarten
        );
        Long otherOutboxId = createDeadLetterOutboxFor(otherParent);

        mockMvc.perform(get("/api/v1/notification-outbox")
                        .with(authenticated(principalMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(otherOutboxId)).doesNotExist());

        mockMvc.perform(post("/api/v1/notification-outbox/{outboxId}/retry", otherOutboxId)
                        .with(authenticated(principalMember))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/notification-outbox/summary")
                        .with(authenticated(otherPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts.DEAD_LETTER").value(1));
    }

    @Test
    @DisplayName("원장은 dead-letter 목록을 채널별로 필터링할 수 있다")
    void principalCanFilterDeadLettersByChannel() throws Exception {
        createDeadLetterOutbox(NotificationChannel.APP);
        Long emailOutboxId = createDeadLetterOutbox(NotificationChannel.EMAIL);

        mockMvc.perform(get("/api/v1/notification-outbox/dead-letters")
                        .param("channel", "EMAIL")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(emailOutboxId))
                .andExpect(jsonPath("$.data.content[0].channel").value("EMAIL"));
    }

    @Test
    @DisplayName("원장은 outbox timeline을 상태, 채널, 검색어로 필터링할 수 있다")
    void principalCanSearchOutboxTimeline() throws Exception {
        createDeliveredOutbox(NotificationChannel.APP, "운영 알림 전달 성공");
        Long deadLetterId = createDeadLetterOutbox(NotificationChannel.EMAIL, "SMTP connection refused");

        mockMvc.perform(get("/api/v1/notification-outbox")
                        .param("status", "DEAD_LETTER")
                        .param("channel", "EMAIL")
                        .param("q", "smtp")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(deadLetterId))
                .andExpect(jsonPath("$.data.content[0].status").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.data.content[0].channel").value("EMAIL"));
    }

    @Test
    @DisplayName("교사는 outbox 운영 API에 접근할 수 없다")
    void teacherCannotAccessOutboxOps() throws Exception {
        mockMvc.perform(get("/api/v1/notification-outbox/summary")
                        .with(authenticated(teacherMember)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A004"));

        mockMvc.perform(get("/api/v1/notification-outbox")
                        .with(authenticated(teacherMember)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("원장은 dead-letter outbox를 즉시 재시도 대기 상태로 되돌릴 수 있다")
    void principalCanRetryDeadLetter() throws Exception {
        Long outboxId = createDeadLetterOutbox();

        mockMvc.perform(post("/api/v1/notification-outbox/{outboxId}/retry", outboxId)
                        .with(authenticated(principalMember))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.lastError").doesNotExist());

        NotificationOutbox outbox = notificationOutboxRepository.findById(outboxId).orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(outbox.getDeadLetteredAt()).isNull();
        assertThat(outbox.canRetry()).isTrue();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_audit_log WHERE target_type = 'NOTIFICATION_OUTBOX' AND target_id = ?",
                Integer.class,
                outboxId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    private Long createDeadLetterOutbox() {
        return createDeadLetterOutbox(NotificationChannel.APP);
    }

    private Long createDeadLetterOutbox(NotificationChannel channel) {
        return createDeadLetterOutbox(channel, "webhook timeout");
    }

    private Long createDeadLetterOutbox(NotificationChannel channel, String errorMessage) {
        Member receiver = memberRepository.findById(parentMember.getId()).orElseThrow();
        Notification notification = notificationRepository.save(Notification.createWithLink(
                receiver,
                NotificationType.SYSTEM,
                "전송 실패 알림",
                "outbox 운영 API 테스트",
                "/notifications"
        ));
        NotificationOutbox outbox = NotificationOutbox.create(notification, channel, 1);
        LocalDateTime now = LocalDateTime.now();
        outbox.markProcessing(now.minusMinutes(1));
        outbox.markDeadLetter(now, errorMessage);
        return notificationOutboxRepository.saveAndFlush(outbox).getId();
    }

    private Long createDeadLetterOutboxFor(Member receiver) {
        Notification notification = notificationRepository.save(Notification.createWithLink(
                receiver,
                NotificationType.SYSTEM,
                "다른 유치원 전송 실패 알림",
                "tenant scope 테스트",
                "/notifications"
        ));
        NotificationOutbox outbox = NotificationOutbox.create(notification, NotificationChannel.APP, 1);
        LocalDateTime now = LocalDateTime.now();
        outbox.markProcessing(now.minusMinutes(1));
        outbox.markDeadLetter(now, "other tenant timeout");
        return notificationOutboxRepository.saveAndFlush(outbox).getId();
    }

    private Long createDeliveredOutbox(NotificationChannel channel, String title) {
        Member receiver = memberRepository.findById(parentMember.getId()).orElseThrow();
        Notification notification = notificationRepository.save(Notification.createWithLink(
                receiver,
                NotificationType.SYSTEM,
                title,
                "outbox timeline 검색 제외 샘플",
                "/notifications"
        ));
        NotificationOutbox outbox = NotificationOutbox.create(notification, channel, 1);
        LocalDateTime now = LocalDateTime.now();
        outbox.markProcessing(now.minusMinutes(1));
        outbox.markDelivered(now);
        return notificationOutboxRepository.saveAndFlush(outbox).getId();
    }
}
