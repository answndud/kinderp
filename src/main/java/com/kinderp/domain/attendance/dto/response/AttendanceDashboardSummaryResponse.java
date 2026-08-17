package com.kinderp.domain.attendance.dto.response;

public record AttendanceDashboardSummaryResponse(
        long presentCount,
        long expectedCount,
        double attendanceRate
) {
}
