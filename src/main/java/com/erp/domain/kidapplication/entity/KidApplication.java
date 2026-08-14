package com.erp.domain.kidapplication.entity;

import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.kid.entity.Gender;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kid_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KidApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Member parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @Column(name = "kid_name", nullable = false, length = 50)
    private String kidName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_classroom_id")
    private Classroom preferredClassroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_classroom_id")
    private Classroom assignedClassroom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "waitlisted_at")
    private LocalDateTime waitlistedAt;

    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(name = "offer_expires_at")
    private LocalDateTime offerExpiresAt;

    @Column(name = "offer_accepted_at")
    private LocalDateTime offerAcceptedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "decision_note")
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private Member processedBy;

    @Column(name = "kid_id")
    private Long kidId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private KidApplication(Member parent, Kindergarten kindergarten, String kidName,
                           LocalDate birthDate, Gender gender, Classroom preferredClassroom,
                           String notes) {
        this.parent = parent;
        this.kindergarten = kindergarten;
        this.kidName = kidName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.preferredClassroom = preferredClassroom;
        this.notes = notes;
        this.status = ApplicationStatus.PENDING;
    }

    public void approveDirect(Classroom classroom, Member processor, Long approvedKidId) {
        ensureState(status.isPending(), "즉시 승인은 PENDING 상태에서만 가능합니다");
        this.status = ApplicationStatus.APPROVED;
        this.assignedClassroom = classroom;
        this.processedAt = ProductTime.nowDateTime();
        this.processedBy = processor;
        this.kidId = approvedKidId;
        this.waitlistedAt = null;
        this.offeredAt = null;
        this.offerExpiresAt = null;
        this.offerAcceptedAt = null;
        this.rejectionReason = null;
    }

    public void placeOnWaitlist(Classroom classroom, Member processor, String note) {
        ensureState(status == ApplicationStatus.PENDING || status == ApplicationStatus.OFFER_EXPIRED,
                "대기열 등록은 PENDING/OFFER_EXPIRED 상태에서만 가능합니다");
        this.status = ApplicationStatus.WAITLISTED;
        this.assignedClassroom = classroom;
        this.processedAt = ProductTime.nowDateTime();
        this.processedBy = processor;
        this.waitlistedAt = ProductTime.nowDateTime();
        this.offeredAt = null;
        this.offerExpiresAt = null;
        this.offerAcceptedAt = null;
        this.rejectionReason = null;
        this.decisionNote = normalize(note);
        this.kidId = null;
    }

    public void offerSeat(Classroom classroom, Member processor, LocalDateTime expiresAt, String note) {
        ensureState(status == ApplicationStatus.PENDING || status == ApplicationStatus.WAITLISTED,
                "입학 offer는 PENDING/WAITLISTED 상태에서만 가능합니다");
        this.status = ApplicationStatus.OFFERED;
        this.assignedClassroom = classroom;
        this.processedAt = ProductTime.nowDateTime();
        this.processedBy = processor;
        this.offeredAt = ProductTime.nowDateTime();
        this.offerExpiresAt = expiresAt;
        this.rejectionReason = null;
        this.decisionNote = normalize(note);
    }

    public void acceptOffer(Long approvedKidId) {
        ensureState(status == ApplicationStatus.OFFERED, "입학 offer 상태가 아닙니다");
        ensureState(offerExpiresAt == null || offerExpiresAt.isAfter(ProductTime.nowDateTime()),
                "만료된 입학 offer는 수락할 수 없습니다");
        this.status = ApplicationStatus.APPROVED;
        this.offerAcceptedAt = ProductTime.nowDateTime();
        this.processedAt = ProductTime.nowDateTime();
        this.kidId = approvedKidId;
    }

    public void markOfferExpired() {
        ensureState(status == ApplicationStatus.OFFERED, "만료 처리 가능한 offer 상태가 아닙니다");
        this.status = ApplicationStatus.OFFER_EXPIRED;
        this.processedAt = ProductTime.nowDateTime();
    }

    public void reject(String reason, Member processor) {
        ensureState(status.isReviewQueueStatus(), "처리 가능한 상태가 아닙니다: " + status);
        this.status = ApplicationStatus.REJECTED;
        this.rejectionReason = reason;
        this.processedAt = ProductTime.nowDateTime();
        this.processedBy = processor;
        this.offerExpiresAt = null;
    }

    public void cancel() {
        ensureState(status.isActiveForParent(), "취소 가능한 상태가 아닙니다: " + status);
        this.status = ApplicationStatus.CANCELLED;
        this.processedAt = ProductTime.nowDateTime();
        this.offerExpiresAt = null;
    }

    public void reapply(Kindergarten kindergarten, String kidName, LocalDate birthDate, Gender gender,
                        Classroom preferredClassroom, String notes) {
        ensureState(status.isCancelled() || status.isRejected() || status.isOfferExpired(),
                "재신청 가능한 상태가 아닙니다: " + status);

        this.kindergarten = kindergarten;
        this.kidName = kidName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.preferredClassroom = preferredClassroom;
        this.notes = notes;
        this.status = ApplicationStatus.PENDING;
        this.assignedClassroom = null;
        this.processedAt = null;
        this.waitlistedAt = null;
        this.offeredAt = null;
        this.offerExpiresAt = null;
        this.offerAcceptedAt = null;
        this.processedBy = null;
        this.rejectionReason = null;
        this.decisionNote = null;
        this.kidId = null;
    }

    public void softDelete() {
        this.deletedAt = ProductTime.nowDateTime();
    }

    public boolean isPending() {
        return status.isPending();
    }

    public boolean isApproved() {
        return status.isApproved();
    }

    public boolean isRejected() {
        return status.isRejected();
    }

    public boolean isWaitlisted() {
        return status.isWaitlisted();
    }

    public boolean isOffered() {
        return status.isOffered();
    }

    public boolean isOfferExpired() {
        return status.isOfferExpired();
    }

    public static KidApplication create(Member parent, Kindergarten kindergarten,
                                        String kidName, LocalDate birthDate, Gender gender,
                                        Classroom preferredClassroom, String notes) {
        return KidApplication.builder()
                .parent(parent)
                .kindergarten(kindergarten)
                .kidName(kidName)
                .birthDate(birthDate)
                .gender(gender)
                .preferredClassroom(preferredClassroom)
                .notes(notes)
                .build();
    }

    private void ensureState(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.APPLICATION_INVALID_STATE, message);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
