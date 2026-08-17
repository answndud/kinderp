package com.kinderp.domain.notification.dto.request;

import com.kinderp.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationCreateRequest(
        @NotNull(message = "수신자 ID는 필수입니다")
        Long receiverId,

        @NotNull(message = "알림 타입은 필수입니다")
        NotificationType type,

        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        String content,

        @Size(max = 500, message = "링크 URL은 500자 이하여야 합니다")
        String linkUrl,

        @Size(max = 50, message = "연관 엔티티 타입은 50자 이하여야 합니다")
        String relatedEntityType,

        Long relatedEntityId
) {
}
