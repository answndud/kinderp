package com.kinderp.domain.kindergartenapplication.entity;

/**
 * 지원서 상태
 */
public enum ApplicationStatus {
    PENDING("승인 대기"),
    APPROVED("승인 완료"),
    REJECTED("거절"),
    CANCELLED("취소");

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

    public boolean isRejected() {
        return this == REJECTED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    public boolean isProcessable() {
        return this == PENDING;
    }
}
