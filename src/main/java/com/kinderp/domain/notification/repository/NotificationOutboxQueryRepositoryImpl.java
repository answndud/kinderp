package com.kinderp.domain.notification.repository;

import com.kinderp.domain.member.entity.QMember;
import com.kinderp.domain.notification.entity.NotificationChannel;
import com.kinderp.domain.notification.entity.NotificationDeliveryStatus;
import com.kinderp.domain.notification.entity.NotificationOutbox;
import com.kinderp.domain.notification.entity.QNotification;
import com.kinderp.domain.notification.entity.QNotificationOutbox;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

@RequiredArgsConstructor
public class NotificationOutboxQueryRepositoryImpl implements NotificationOutboxQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * status·channel·keyword는 값이 있을 때만 조건에 붙는다.
     * keyword는 양쪽 와일드카드 LIKE라 인덱스를 타지 못하므로, 유치원(tenant) 조건으로 먼저 범위를 줄인 뒤 적용한다.
     */
    @Override
    public Page<NotificationOutbox> searchTimeline(Long kindergartenId,
                                                   NotificationDeliveryStatus status,
                                                   NotificationChannel channel,
                                                   String keyword,
                                                   Pageable pageable) {
        QNotificationOutbox outbox = QNotificationOutbox.notificationOutbox;
        QNotification notification = QNotification.notification;
        QMember receiver = QMember.member;

        JPAQuery<NotificationOutbox> contentQuery = queryFactory
                .selectFrom(outbox)
                .join(outbox.notification, notification)
                .join(notification.receiver, receiver)
                .where(
                        receiver.kindergarten.id.eq(kindergartenId),
                        statusEq(outbox, status),
                        channelEq(outbox, channel),
                        keywordMatches(outbox, keyword)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());
        applySort(contentQuery, pageable);
        List<NotificationOutbox> content = contentQuery.fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(outbox.count())
                .from(outbox)
                .join(outbox.notification, notification)
                .join(notification.receiver, receiver)
                .where(
                        receiver.kindergarten.id.eq(kindergartenId),
                        statusEq(outbox, status),
                        channelEq(outbox, channel),
                        keywordMatches(outbox, keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = countQuery.fetchOne();
            return total == null ? 0L : total;
        });
    }

    private BooleanExpression statusEq(QNotificationOutbox outbox, NotificationDeliveryStatus status) {
        return status == null ? null : outbox.status.eq(status);
    }

    private BooleanExpression channelEq(QNotificationOutbox outbox, NotificationChannel channel) {
        return channel == null ? null : outbox.channel.eq(channel);
    }

    private BooleanExpression keywordMatches(QNotificationOutbox outbox, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return outbox.title.containsIgnoreCase(keyword)
                .or(outbox.content.containsIgnoreCase(keyword))
                .or(outbox.receiverEmail.containsIgnoreCase(keyword))
                .or(outbox.receiverName.containsIgnoreCase(keyword))
                .or(outbox.lastError.containsIgnoreCase(keyword));
    }

    private void applySort(JPAQuery<NotificationOutbox> query, Pageable pageable) {
        PathBuilder<NotificationOutbox> path = new PathBuilder<>(NotificationOutbox.class, "notificationOutbox");
        pageable.getSort().forEach(order -> query.orderBy(new OrderSpecifier<>(
                order.isAscending() ? Order.ASC : Order.DESC,
                Expressions.comparablePath(Comparable.class, path, order.getProperty()))));
    }
}
