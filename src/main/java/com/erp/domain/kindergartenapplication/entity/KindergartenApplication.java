package com.erp.domain.kindergartenapplication.entity;

import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.member.entity.Member;
import com.erp.global.common.BaseEntity;
import com.erp.global.common.ProductTime;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "kindergarten_application",
       uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "kindergarten_id"}, name = "uk_teacher_kindergarten_active"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KindergartenApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 지원 교사
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Member teacher;

    /**
     * 대상 유치원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    /**
     * 신청 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    /**
     * 지원 메시지 (자기소개 등)
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * 승인/거절 일시
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * 거절 사유
     */
    @Column(name = "rejection_reason")
    private String rejectionReason;

    /**
     * 처리자 (원장)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private Member processedBy;

    /**
     * 삭제일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private KindergartenApplication(Member teacher, Kindergarten kindergarten, String message) {
        this.teacher = teacher;
        this.kindergarten = kindergarten;
        this.message = message;
        this.status = ApplicationStatus.PENDING;
    }

    /**
     * 지원서 승인
     */
    public void approve(Member principal) {
        ensureProcessable("처리 가능한 상태가 아닙니다: " + status);
        this.status = ApplicationStatus.APPROVED;
        this.processedAt = ProductTime.nowDateTime();
        this.processedBy = principal;
    }

    /**
     * 지원서 거절
     */
    public void reject(String reason, Member principal) {
        ensureProcessable("처리 가능한 상태가 아닙니다: " + status);
        this.status = ApplicationStatus.REJECTED;
        this.rejectionReason = reason;
        this.processedAt = ProductTime.nowDateTime();
        this.processedBy = principal;
    }

    /**
     * 지원서 취소
     */
    public void cancel() {
        ensureProcessable("취소 가능한 상태가 아닙니다: " + status);
        this.status = ApplicationStatus.CANCELLED;
        this.processedAt = ProductTime.nowDateTime();
    }

    /**
     * 취소/거절된 지원서 재신청
     */
    public void reapply(String message) {
        if (!(status.isCancelled() || status.isRejected())) {
            throw new BusinessException(ErrorCode.APPLICATION_INVALID_STATE, "재신청 가능한 상태가 아닙니다: " + status);
        }

        this.status = ApplicationStatus.PENDING;
        this.message = message;
        this.processedAt = null;
        this.processedBy = null;
        this.rejectionReason = null;
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
        return deletedAt != null;
    }

    /**
     * 대기 상태 확인
     */
    public boolean isPending() {
        return status.isPending();
    }

    /**
     * 승인 완료 확인
     */
    public boolean isApproved() {
        return status.isApproved();
    }

    /**
     * 거절 확인
     */
    public boolean isRejected() {
        return status.isRejected();
    }

    /**
     * 지원서 생성 정적 팩토리 메서드
     */
    public static KindergartenApplication create(Member teacher, Kindergarten kindergarten, String message) {
        return KindergartenApplication.builder()
                .teacher(teacher)
                .kindergarten(kindergarten)
                .message(message)
                .build();
    }

    private void ensureProcessable(String message) {
        if (!status.isProcessable()) {
            throw new BusinessException(ErrorCode.APPLICATION_INVALID_STATE, message);
        }
    }
}
