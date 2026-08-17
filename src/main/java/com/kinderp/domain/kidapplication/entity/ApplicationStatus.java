package com.kinderp.domain.kidapplication.entity;

/**
 * 입학 신청서 상태
 */
public enum ApplicationStatus {
    PENDING("승인 대기"),
    WAITLISTED("대기열 등록"),
    OFFERED("입학 제안"),
    APPROVED("승인 완료"),
    REJECTED("거절"),
    CANCELLED("취소"),
    OFFER_EXPIRED("제안 만료");

    private final String description;

    ApplicationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isWaitlisted() {
        return this == WAITLISTED;
    }

    public boolean isOffered() {
        return this == OFFERED;
    }

    public boolean isRejected() {
        return this == REJECTED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    public boolean isOfferExpired() {
        return this == OFFER_EXPIRED;
    }

    public boolean isActiveForParent() {
        return this == PENDING || this == WAITLISTED || this == OFFERED;
    }

    public boolean isReviewQueueStatus() {
        return this == PENDING || this == WAITLISTED || this == OFFERED;
    }
}
