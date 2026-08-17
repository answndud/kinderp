package com.kinderp.domain.notification.dto.response;

import java.util.Map;

public record NotificationOutboxSummaryResponse(
        Map<String, Long> statusCounts,
        Map<String, Long> deadLetterCountsByChannel
) {
}
