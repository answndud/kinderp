package com.kinderp.domain.notification.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationOutboxViewController {

    @GetMapping("/notification-outbox")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public String notificationOutboxPage() {
        return "notifications/outbox";
    }
}
