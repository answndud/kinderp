package com.kinderp.domain.notification.dto.response;

public record UnreadCountResponse(
        long count
) {
    public static UnreadCountResponse of(long count) {
        return new UnreadCountResponse(count);
    }
}
