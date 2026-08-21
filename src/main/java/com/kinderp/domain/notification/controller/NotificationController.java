package com.kinderp.domain.notification.controller;

import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.security.user.CustomUserDetails;
import com.kinderp.domain.notification.dto.request.NotificationCreateRequest;
import com.kinderp.domain.notification.dto.response.NotificationResponse;
import com.kinderp.domain.notification.dto.response.UnreadCountResponse;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.domain.notification.service.NotificationRateLimitService;
import com.kinderp.global.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRateLimitService notificationRateLimitService;
    private final ClientIpResolver clientIpResolver;

    /**
     * 알림 생성 (관리자용 또는 내부 호출)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<Long>> createNotification(
            @Valid @RequestBody NotificationCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpServletRequest) {
        notificationRateLimitService.validateCreateAllowed(
                userDetails.getMemberId(),
                clientIpResolver.resolve(httpServletRequest)
        );
        Long notificationId = notificationService.create(request, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(notificationId));
    }

    /**
     * 알림 목록 조회
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<NotificationResponse> notifications = notificationService.getNotifications(userDetails.getMemberId(), limit);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    /**
     * 알림 상세 조회
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NotificationResponse notification = notificationService.getNotification(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(notification));
    }

    /**
     * 안 읽은 알림 개수
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UnreadCountResponse response = notificationService.getUnreadCount(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 안 읽은 알림 목록
     */
    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    /**
     * 알림 읽음 표시
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAsRead(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 전체 읽음 표시
     */
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 알림 삭제 (Soft Delete)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.delete(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
