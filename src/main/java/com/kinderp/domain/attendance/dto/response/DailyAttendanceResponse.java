package com.kinderp.domain.attendance.dto.response;

import com.kinderp.domain.attendance.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 일별 출석 간단 응답 DTO
 */
public record DailyAttendanceResponse(
        Long kidId,
        String kidName,
        Long attendanceId,
        AttendanceStatus status,
        String statusDescription,
        LocalTime dropOffTime,
        LocalTime pickUpTime,
        String note
) {
    /**
     * Attendance 엔티티를 간단 DTO로 변환
     */
    public static DailyAttendanceResponse from(com.kinderp.domain.attendance.entity.Attendance attendance) {
        return new DailyAttendanceResponse(
                attendance.getKid().getId(),
                attendance.getKid().getName(),
                attendance.getId(),
                attendance.getStatus(),
                attendance.getStatus() != null ? attendance.getStatus().getDescription() : null,
                attendance.getDropOffTime(),
                attendance.getPickUpTime(),
                attendance.getNote()
        );
    }

    public static DailyAttendanceResponse from(com.kinderp.domain.kid.entity.Kid kid,
                                               com.kinderp.domain.attendance.entity.Attendance attendance) {
        if (attendance == null) {
            return new DailyAttendanceResponse(
                    kid.getId(),
                    kid.getName(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return from(attendance);
    }
}
