package com.erp.domain.attendance.dto.response;

public record AttendanceDashboardSummaryResponse(
        long presentCount,
        long expectedCount,
        double attendanceRate
) {
}
