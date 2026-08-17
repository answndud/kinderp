package com.kinderp.domain.dashboard.service;

import com.kinderp.domain.announcement.repository.AnnouncementRepository;
import com.kinderp.domain.attendance.repository.AttendanceRepository;
import com.kinderp.domain.dashboard.dto.response.DashboardStatisticsResponse;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.common.ProductTime;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final MemberRepository memberRepository;
    private final KidRepository kidRepository;
    private final AttendanceRepository attendanceRepository;
    private final AnnouncementRepository announcementRepository;

    @Cacheable(cacheNames = "dashboardStatistics", key = "#kindergarten.id")
    public DashboardStatisticsResponse getDashboardStatistics(Kindergarten kindergarten) {
        long kindergartenId = kindergarten.getId();
        LocalDate today = ProductTime.today();
        LocalDate attendanceEndDate = today.minusDays(1);
        LocalDate attendanceStartDate30Days = attendanceEndDate.minusDays(29);

        var kidSummaries = kidRepository.findDashboardKidSummaries(kindergartenId);
        var memberCounts = memberRepository.findDashboardMemberCounts(kindergartenId);
        var attendanceCounts = attendanceRepository.findPresentOrLateCountsByKindergartenAndDateBetween(
                kindergartenId,
                attendanceStartDate30Days,
                attendanceEndDate
        );
        Map<LocalDate, Long> attendanceCountByDate = attendanceCounts.stream()
                .collect(Collectors.toMap(
                        AttendanceRepository.DailyPresentCountProjection::getDate,
                        AttendanceRepository.DailyPresentCountProjection::getPresentCount
                ));
        var announcementStats = announcementRepository.findDashboardStats(kindergartenId);

        int totalKids = Math.toIntExact(kidSummaries.stream()
                .filter(summary -> isCurrentlyEnrolled(summary, today))
                .count());
        int totalTeachers = Math.toIntExact(memberCounts.getTotalTeachers());
        int totalParents = Math.toIntExact(memberCounts.getTotalParents());
        int totalAnnouncements = Math.toIntExact(announcementStats.getTotalAnnouncements());
        int todayAttendanceCount = (int) attendanceRepository.countByKidClassroomKindergartenIdAndDate(kindergartenId, today);

        double attendanceRate7Days = calculateAttendanceRate(
                kidSummaries,
                attendanceCountByDate,
                attendanceEndDate.minusDays(6),
                attendanceEndDate
        );
        double attendanceRate30Days = calculateAttendanceRate(
                kidSummaries,
                attendanceCountByDate,
                attendanceStartDate30Days,
                attendanceEndDate
        );
        double announcementReadRate = calculateAnnouncementReadRate(
                totalAnnouncements,
                memberCounts.getTotalMembers(),
                announcementStats.getUniqueReadCount()
        );

        return new DashboardStatisticsResponse(
                totalKids,
                totalTeachers,
                totalParents,
                attendanceRate7Days,
                attendanceRate30Days,
                announcementReadRate,
                totalAnnouncements,
                todayAttendanceCount
        );
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboardStatistics", key = "#kindergartenId")
    public void evictDashboardStatisticsCache(Long kindergartenId) {
    }

    private double calculateAttendanceRate(
            java.util.List<KidRepository.DashboardKidSummaryProjection> kidSummaries,
            Map<LocalDate, Long> attendanceCountByDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        long totalPossibleAttendance = countActiveKidSchoolDays(kidSummaries, startDate, endDate);
        if (totalPossibleAttendance <= 0) {
            return 0.0;
        }

        long totalAttendance = sumAttendanceCounts(attendanceCountByDate, startDate, endDate);

        return (totalAttendance * 100.0) / totalPossibleAttendance;
    }

    private double calculateAnnouncementReadRate(long totalAnnouncements, long totalMembers, long uniqueReadCount) {
        if (totalAnnouncements == 0 || totalMembers == 0 || uniqueReadCount == 0) {
            return 0.0;
        }

        long totalReachableReads = totalMembers * totalAnnouncements;
        return (uniqueReadCount * 100.0) / totalReachableReads;
    }

    private long countActiveKidSchoolDays(
            java.util.List<KidRepository.DashboardKidSummaryProjection> kidSummaries,
            LocalDate startDate,
            LocalDate endDate
    ) {
        long total = 0L;

        for (KidRepository.DashboardKidSummaryProjection summary : kidSummaries) {
            LocalDate activeStart = maxDate(startDate, summary.getAdmissionDate());
            LocalDate deletedDate = summary.getDeletedAt() != null ? summary.getDeletedAt().toLocalDate() : null;
            LocalDate activeEnd = deletedDate != null ? minDate(endDate, deletedDate) : endDate;

            if (activeStart.isAfter(activeEnd)) {
                continue;
            }

            for (LocalDate date = activeStart; !date.isAfter(activeEnd); date = date.plusDays(1)) {
                if (isSchoolDay(date)) {
                    total++;
                }
            }
        }

        return total;
    }

    private long sumAttendanceCounts(Map<LocalDate, Long> attendanceCountByDate, LocalDate startDate, LocalDate endDate) {
        long total = 0L;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!isSchoolDay(date)) {
                continue;
            }
            total += attendanceCountByDate.getOrDefault(date, 0L);
        }

        return total;
    }

    private boolean isCurrentlyEnrolled(KidRepository.DashboardKidSummaryProjection summary, LocalDate today) {
        return summary.getAdmissionDate() != null
                && !summary.getAdmissionDate().isAfter(today)
                && summary.getDeletedAt() == null;
    }

    private boolean isSchoolDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private LocalDate maxDate(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDate minDate(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }
}
