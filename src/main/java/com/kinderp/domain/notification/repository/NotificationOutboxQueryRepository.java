package com.kinderp.domain.notification.repository;

import com.kinderp.domain.notification.entity.NotificationChannel;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 조건이 선택적으로 붙는 outbox 운영 검색 (QueryDSL 구현은 {@link NotificationOutboxQueryRepositoryImpl}).
 */
public interface NotificationOutboxQueryRepository {

    Page<NotificationOutbox> searchTimeline(Long kindergartenId,
                                            NotificationDeliveryStatus status,
                                            NotificationChannel channel,
                                            String keyword,
                                            Pageable pageable);
}
