package com.kinderp.domain.notification.controller;

import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class NotificationViewController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    public String notificationsPage(Model model) {
        model.addAttribute("notificationTypes", NotificationType.values());
        return "notifications/index";
    }

    @GetMapping("/notifications/fragments/badge")
    @PreAuthorize("isAuthenticated()")
    public String badge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        long count = 0;
        if (userDetails != null) {
            count = notificationService.getUnreadCount(userDetails.getMemberId()).count();
        }

        model.addAttribute("unreadCount", count);
        return "notifications/fragments/badge :: badge";
    }

    @GetMapping("/notifications/fragments/list")
    @PreAuthorize("isAuthenticated()")
    public String list(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "type", required = false) NotificationType type,
            @RequestParam(name = "showFilters", defaultValue = "false") boolean showFilters,
            Model model) {

        if (userDetails == null) {
            model.addAttribute("notifications", java.util.List.of());
            model.addAttribute("unreadOnly", unreadOnly);
            model.addAttribute("unreadCount", 0);
            model.addAttribute("type", type);
            model.addAttribute("showFilters", showFilters);
            model.addAttribute("notificationTypes", NotificationType.values());
            return "notifications/fragments/list :: list";
        }

        if (unreadOnly && type != null) {
            model.addAttribute("notifications", notificationService.getUnreadNotificationsByType(userDetails.getMemberId(), type, limit));
        } else if (unreadOnly) {
            model.addAttribute("notifications", notificationService.getUnreadNotifications(userDetails.getMemberId(), limit));
        } else if (type != null) {
            model.addAttribute("notifications", notificationService.getNotificationsByType(userDetails.getMemberId(), type, limit));
        } else {
            model.addAttribute("notifications", notificationService.getNotifications(userDetails.getMemberId(), limit));
        }

        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("type", type);
        model.addAttribute("showFilters", showFilters);
        model.addAttribute("notificationTypes", NotificationType.values());
        model.addAttribute("unreadCount", notificationService.getUnreadCount(userDetails.getMemberId()).count());
        return "notifications/fragments/list :: list";
    }
}
