package com.erp.domain.notification.service.channel;

import com.erp.domain.notification.config.NotificationDeliveryProperties;
import com.erp.domain.notification.entity.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.delivery.email", name = "enabled", havingValue = "true")
public class EmailNotificationSender implements NotificationChannelSender {

    private final JavaMailSender mailSender;
    private final NotificationDeliveryProperties deliveryProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationDeliveryPayload payload) {
        if (payload.receiverEmail() == null || payload.receiverEmail().isBlank()) {
            throw new IllegalStateException("Notification receiver email is empty");
        }

        String from = deliveryProperties.getEmail().getFrom();
        String subjectPrefix = deliveryProperties.getEmail().getSubjectPrefix();
        String subject = (subjectPrefix != null ? subjectPrefix + " " : "") + payload.title();
        String body = buildBody(payload);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(payload.receiverEmail());
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.debug("Email notification dispatched. receiverId={}", payload.receiverId());
        } catch (MessagingException e) {
            throw new IllegalStateException("Email notification failed: " + e.getMessage(), e);
        }
    }

    private String buildBody(NotificationDeliveryPayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append(payload.content() != null ? payload.content() : "");
        if (payload.linkUrl() != null && !payload.linkUrl().isBlank()) {
            builder.append("\n\n바로가기: ").append(payload.linkUrl());
        }
        return builder.toString();
    }
}
