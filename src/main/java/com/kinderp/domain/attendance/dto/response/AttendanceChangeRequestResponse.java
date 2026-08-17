package com.kinderp.domain.attendance.dto.response;

import com.kinderp.domain.attendance.entity.AttendanceChangeRequest;
import com.kinderp.domain.attendance.entity.AttendanceChangeRequestStatus;
import com.kinderp.domain.attendance.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceChangeRequestResponse(
        Long id,
        Long kindergartenId,
        Long classroomId,
        KidInfo kid,
        MemberInfo requester,
        MemberInfo reviewedBy,
        Long attendanceId,
        LocalDate date,
        AttendanceStatus requestedStatus,
        LocalTime requestedDropOffTime,
        LocalTime requestedPickUpTime,
        String note,
        AttendanceChangeRequestStatus status,
        String rejectionReason,
        LocalDateTime reviewedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {
    public static AttendanceChangeRequestResponse from(AttendanceChangeRequest request) {
        return new AttendanceChangeRequestResponse(
                request.getId(),
                request.getKindergartenId(),
                request.getClassroomId(),
                new KidInfo(request.getKid().getId(), request.getKid().getName()),
                new MemberInfo(request.getRequester().getId(), request.getRequester().getName()),
                request.getReviewedBy() == null ? null : new MemberInfo(request.getReviewedBy().getId(), request.getReviewedBy().getName()),
                request.getAttendanceId(),
                request.getDate(),
                request.getRequestedStatus(),
                request.getRequestedDropOffTime(),
                request.getRequestedPickUpTime(),
                request.getNote(),
                request.getStatus(),
                request.getRejectionReason(),
                request.getReviewedAt(),
                request.getCancelledAt(),
                request.getCreatedAt()
        );
    }

    public record KidInfo(Long id, String name) {
    }

    public record MemberInfo(Long id, String name) {
    }
}
