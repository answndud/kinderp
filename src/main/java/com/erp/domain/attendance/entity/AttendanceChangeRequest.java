package com.erp.domain.attendance.entity;

import com.erp.domain.kid.entity.Kid;
import com.erp.domain.member.entity.Member;
import com.erp.global.common.BaseEntity;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_change_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceChangeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kindergarten_id", nullable = false)
    private Long kindergartenId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kid_id", nullable = false)
    private Kid kid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Member reviewedBy;

    @Column(name = "attendance_id")
    private Long attendanceId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status", nullable = false, length = 20)
    private AttendanceStatus requestedStatus;

    @Column(name = "requested_drop_off_time")
    private LocalTime requestedDropOffTime;

    @Column(name = "requested_pick_up_time")
    private LocalTime requestedPickUpTime;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceChangeRequestStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    private AttendanceChangeRequest(Long kindergartenId,
                                    Long classroomId,
                                    Kid kid,
                                    Member requester,
                                    LocalDate date,
                                    AttendanceStatus requestedStatus,
                                    LocalTime requestedDropOffTime,
                                    LocalTime requestedPickUpTime,
                                    String note,
                                    String idempotencyKey) {
        this.kindergartenId = kindergartenId;
        this.classroomId = classroomId;
        this.kid = kid;
        this.requester = requester;
        this.date = date;
        this.requestedStatus = requestedStatus;
        this.requestedDropOffTime = requestedDropOffTime;
        this.requestedPickUpTime = requestedPickUpTime;
        this.note = note;
        this.idempotencyKey = idempotencyKey;
        this.status = AttendanceChangeRequestStatus.PENDING;
    }

    public void approve(Member reviewer, Long approvedAttendanceId) {
        ensurePending();
        this.status = AttendanceChangeRequestStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.attendanceId = approvedAttendanceId;
        this.rejectionReason = null;
        this.cancelledAt = null;
    }

    public void reject(Member reviewer, String rejectionReason) {
        ensurePending();
        this.status = AttendanceChangeRequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
        this.cancelledAt = null;
    }

    public void cancel() {
        ensurePending();
        this.status = AttendanceChangeRequestStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status.isPending();
    }

    public static AttendanceChangeRequest create(Kid kid,
                                                 Member requester,
                                                 LocalDate date,
                                                 AttendanceStatus requestedStatus,
                                                 LocalTime requestedDropOffTime,
                                                 LocalTime requestedPickUpTime,
                                                 String note,
                                                 String idempotencyKey) {
        return AttendanceChangeRequest.builder()
                .kindergartenId(kid.getClassroom().getKindergarten().getId())
                .classroomId(kid.getClassroom().getId())
                .kid(kid)
                .requester(requester)
                .date(date)
                .requestedStatus(requestedStatus)
                .requestedDropOffTime(requestedDropOffTime)
                .requestedPickUpTime(requestedPickUpTime)
                .note(note)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    public static AttendanceChangeRequest create(Kid kid,
                                                 Member requester,
                                                 LocalDate date,
                                                 AttendanceStatus requestedStatus,
                                                 LocalTime requestedDropOffTime,
                                                 LocalTime requestedPickUpTime,
                                                 String note) {
        return create(kid, requester, date, requestedStatus, requestedDropOffTime,
                requestedPickUpTime, note, null);
    }

    private void ensurePending() {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_PENDING);
        }
    }
}
