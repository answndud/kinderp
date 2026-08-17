package com.kinderp.domain.attendance.dto.response;

import com.kinderp.domain.attendance.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 출석 정보 응답 DTO
 */
public record AttendanceResponse(
        Long id,
        Long kidId,
        String kidName,
        LocalDate date,
        AttendanceStatus status,
        String statusDescription,
        LocalTime dropOffTime,
        LocalTime pickUpTime,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Attendance 엔티티를 DTO로 변환
     */
    public static AttendanceResponse from(com.kinderp.domain.attendance.entity.Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getKid().getId(),
                attendance.getKid().getName(),
                attendance.getDate(),
                attendance.getStatus(),
                attendance.getStatus().getDescription(),
                attendance.getDropOffTime(),
                attendance.getPickUpTime(),
                attendance.getNote(),
                attendance.getCreatedAt(),
                attendance.getUpdatedAt()
        );
    }
}
