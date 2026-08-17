package com.kinderp.domain.dashboard.dto.response;

/**
 * 대시보드 통계 응답 DTO
 */
public record DashboardStatisticsResponse(
        int totalKids,              // 총 원생 수
        int totalTeachers,           // 총 교사 수
        int totalParents,            // 총 학부모 수
        double attendanceRate7Days,  // 최근 7일 출석률 (%)
        double attendanceRate30Days, // 최근 30일 출석률 (%)
        double announcementReadRate, // 공지 고유 열람률 (%)
        int totalAnnouncements,      // 총 공지 수
        int todayAttendanceCount     // 오늘 출석한 원생 수
) {
}
