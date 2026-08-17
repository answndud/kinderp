package com.kinderp.domain.attendance.dto.response;

public record MonthlyAttendanceKidReportResponse(
        Long kidId,
        String kidName,
        int presentDays,
        int absentDays,
        int lateDays,
        int earlyLeaveDays,
        int sickLeaveDays,
        int totalDays
) {
}
