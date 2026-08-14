package com.erp.domain.notification.entity;

import com.erp.domain.member.entity.Member;
import com.erp.global.common.BaseEntity;
import com.erp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 수신자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    /**
     * 알림 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    /**
     * 제목
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 내용
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 링크 URL (관련 페이지로 이동)
     */
    @Column(name = "link_url")
    private String linkUrl;

    /**
     * 읽음 여부
     */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /**
     * 읽은 일시
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 연관 엔티티 타입
     */
    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    /**
     * 연관 엔티티 ID
     */
    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    /**
     * 삭제일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Notification(Member receiver, NotificationType type, String title, String content,
                        String linkUrl, String relatedEntityType, Long relatedEntityId) {
        this.receiver = receiver;
        this.type = type;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
        this.isRead = false;
    }

    /**
     * 읽음 표시
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = ProductTime.nowDateTime();
        }
    }

    /**
     * 읽음 취소 (테스트용)
     */
    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
    }

    /**
     * Soft Delete
     */
    public void softDelete() {
        this.deletedAt = ProductTime.nowDateTime();
    }

    /**
     * 삭제 여부 확인
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * 안 읽은 알림 확인
     */
    public boolean isUnread() {
        return !this.isRead;
    }

    /**
     * 알림 생성 정적 팩토리 메서드
     */
    public static Notification create(Member receiver, NotificationType type, String title, String content) {
        return Notification.builder()
                .receiver(receiver)
                .type(type)
                .title(title)
                .content(content)
                .build();
    }

    /**
     * 연관 엔티티 포함 알림 생성
     */
    public static Notification createWithRelatedEntity(Member receiver, NotificationType type,
                                                        String title, String content,
                                                        String relatedEntityType, Long relatedEntityId) {
        return Notification.builder()
                .receiver(receiver)
                .type(type)
                .title(title)
                .content(content)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build();
    }

    /**
     * 링크 포함 알림 생성
     */
    public static Notification createWithLink(Member receiver, NotificationType type,
                                              String title, String content, String linkUrl) {
        return Notification.builder()
                .receiver(receiver)
                .type(type)
                .title(title)
                .content(content)
                .linkUrl(linkUrl)
                .build();
    }
}
