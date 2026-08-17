package com.kinderp.domain.attendance.entity;

import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 출석 엔티티
 */
@Entity
@Table(name = "attendance", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"kid_id", "date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 원생
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kid_id", nullable = false)
    private Kid kid;

    /**
     * 날짜
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * 출석 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    /**
     * 등원 시간
     */
    @Column(name = "drop_off_time")
    private LocalTime dropOffTime;

    /**
     * 하원 시간
     */
    @Column(name = "pick_up_time")
    private LocalTime pickUpTime;

    /**
     * 메모 (결석 사유 등)
     */
    @Column(name = "note", length = 255)
    private String note;

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 출석 정보 생성
     */
    public static Attendance create(Kid kid, LocalDate date, AttendanceStatus status) {
        Attendance attendance = new Attendance();
        attendance.kid = kid;
        attendance.date = date;
        attendance.status = status;
        return attendance;
    }

    /**
     * 등원 기록
     */
    public static Attendance createDropOff(Kid kid, LocalDate date, LocalTime dropOffTime) {
        Attendance attendance = new Attendance();
        attendance.kid = kid;
        attendance.date = date;
        attendance.dropOffTime = dropOffTime;
        attendance.status = AttendanceStatus.PRESENT;
        return attendance;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 출석 정보 수정
     */
    public void updateAttendance(AttendanceStatus status, String note) {
        this.status = status;
        this.note = note;
    }

    /**
     * 등원 시간 기록
     */
    public void recordDropOff(LocalTime dropOffTime) {
        this.dropOffTime = dropOffTime;
        if (this.status == null) {
            this.status = AttendanceStatus.PRESENT;
        }
    }

    /**
     * 하원 시간 기록
     */
    public void recordPickUp(LocalTime pickUpTime) {
        this.pickUpTime = pickUpTime;
    }

    /**
     * 결석 처리
     */
    public void markAbsent(String note) {
        this.status = AttendanceStatus.ABSENT;
        this.note = note;
        this.dropOffTime = null;
        this.pickUpTime = null;
    }

    /**
     * 지각 처리
     */
    public void markLate(LocalTime dropOffTime, String note) {
        this.status = AttendanceStatus.LATE;
        this.dropOffTime = dropOffTime;
        this.note = note;
    }

    /**
     * 조퇴 처리
     */
    public void markEarlyLeave(LocalTime pickUpTime, String note) {
        this.status = AttendanceStatus.EARLY_LEAVE;
        this.pickUpTime = pickUpTime;
        this.note = note;
    }

    /**
     * 병결 처리
     */
    public void markSickLeave(String note) {
        this.status = AttendanceStatus.SICK_LEAVE;
        this.note = note;
        this.dropOffTime = null;
        this.pickUpTime = null;
    }

    /**
     * 등원 완료 여부
     */
    public boolean isDroppedOff() {
        return dropOffTime != null;
    }

    /**
     * 하원 완료 여부
     */
    public boolean isPickedUp() {
        return pickUpTime != null;
    }

    /**
     * 출석 상태 확인
     */
    public boolean isPresent() {
        return status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE;
    }

    /**
     * 결석 상태 확인
     */
    public boolean isAbsent() {
        return status == AttendanceStatus.ABSENT || status == AttendanceStatus.SICK_LEAVE;
    }
}
