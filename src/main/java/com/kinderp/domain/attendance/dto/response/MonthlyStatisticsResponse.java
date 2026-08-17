package com.kinderp.domain.attendance.dto.response;

/**
 * 월간 출석 통계 응답 DTO
 */
public record MonthlyStatisticsResponse(
        Long kidId,
        String kidName,
        int year,
        int month,
        int presentDays,      // 출석일수 (지각 포함)
        int absentDays,       // 결석일수 (병결 포함)
        int lateDays,         // 지각일수
        int sickLeaveDays,    // 병결일수
        int totalDays         // 전체 등록일수
) {
}
