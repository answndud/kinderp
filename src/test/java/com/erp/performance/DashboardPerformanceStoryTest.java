package com.erp.performance;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.announcement.dto.request.AnnouncementRequest;
import com.erp.domain.announcement.entity.Announcement;
import com.erp.domain.attendance.entity.AttendanceStatus;
import com.erp.domain.attendance.service.AttendanceService;
import com.erp.domain.announcement.service.AnnouncementService;
import com.erp.domain.dashboard.service.DashboardService;
import com.erp.domain.kid.entity.Kid;
import com.erp.domain.kid.entity.Gender;
import com.erp.domain.member.entity.MemberRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("성능 스토리 - 대시보드 통계")
@Tag("performance")
class DashboardPerformanceStoryTest extends BaseIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AnnouncementService announcementService;

    @Test
    @DisplayName("통계 집계 경로 - 레거시 대비 쿼리 수/응답시간 비교")
    void compareLegacyVsOptimizedDashboardStatistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        // warm-up
        legacyDashboardStatistics(kindergarten.getId());
        clearDashboardCache();
        dashboardService.getDashboardStatistics(kindergarten);

        Measurement legacy = measure(statistics, () -> legacyDashboardStatistics(kindergarten.getId()));
        clearDashboardCache();
        Measurement optimized = measure(statistics, () -> dashboardService.getDashboardStatistics(kindergarten));

        System.out.printf("[PERF] dashboard-legacy    - queries=%d, elapsedMs=%d%n", legacy.queryCount, legacy.elapsedMs);
        System.out.printf("[PERF] dashboard-optimized - queries=%d, elapsedMs=%d%n", optimized.queryCount, optimized.elapsedMs);

        assertTrue(optimized.queryCount < legacy.queryCount,
                "optimized dashboard path must use fewer queries than legacy path");
    }

    @Test
    @DisplayName("대시보드 캐시 적용 - 동일 키 재조회 시 쿼리 감소")
    void dashboardCacheHit_ReducesQueries() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        clearDashboardCache();

        Measurement firstCall = measure(statistics, () -> dashboardService.getDashboardStatistics(kindergarten));
        Measurement secondCall = measure(statistics, () -> dashboardService.getDashboardStatistics(kindergarten));

        System.out.printf("[PERF] dashboard-cache-miss - queries=%d, elapsedMs=%d%n", firstCall.queryCount, firstCall.elapsedMs);
        System.out.printf("[PERF] dashboard-cache-hit  - queries=%d, elapsedMs=%d%n", secondCall.queryCount, secondCall.elapsedMs);

        assertTrue(secondCall.queryCount < firstCall.queryCount,
                "cache hit path must use fewer queries than cache miss path");
    }

    @Test
    @DisplayName("출석 변경 시 대시보드 캐시 무효화 - todayAttendanceCount 반영")
    void dashboardCacheEvictedOnAttendanceWrite() {
        clearDashboardCache();

        var before = dashboardService.getDashboardStatistics(kindergarten);
        attendanceService.markAbsent(kid.getId(), LocalDate.now(), "캐시 무효화 테스트", teacherMember.getId());
        var after = dashboardService.getDashboardStatistics(kindergarten);

        assertTrue(after.todayAttendanceCount() > before.todayAttendanceCount(),
                "attendance write should evict dashboard cache and refresh todayAttendanceCount");
    }

    @Test
    @DisplayName("공지 변경 시 대시보드 캐시 무효화 - totalAnnouncements 반영")
    void dashboardCacheEvictedOnAnnouncementWrite() {
        clearDashboardCache();

        var before = dashboardService.getDashboardStatistics(kindergarten);

        AnnouncementRequest request = new AnnouncementRequest();
        request.setKindergartenId(kindergarten.getId());
        request.setTitle("캐시 무효화 공지");
        request.setContent("대시보드 캐시 무효화 검증용 공지입니다.");
        request.setIsImportant(false);

        announcementService.createAnnouncement(request, principalMember.getId());

        var after = dashboardService.getDashboardStatistics(kindergarten);

        assertTrue(after.totalAnnouncements() > before.totalAnnouncements(),
                "announcement write should evict dashboard cache and refresh totalAnnouncements");
    }

    @Test
    @DisplayName("대규모 원생 fixture에서도 대시보드 집계 쿼리 예산은 일정하다")
    void dashboardStatistics_StaysWithinQueryBudgetForLargeFixture() {
        List<Kid> created = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            created.add(Kid.create(
                    classroom,
                    "대규모 성능 원생 " + i,
                    LocalDate.of(2020, 1, 1),
                    Gender.MALE,
                    LocalDate.now()
            ));
        }
        kidRepository.saveAll(created);
        entityManager.flush();
        entityManager.clear();
        clearDashboardCache();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        Measurement optimized = measure(statistics, () -> dashboardService.getDashboardStatistics(kindergarten));

        System.out.printf("[PERF] dashboard-large-fixture - rows=%d, queries=%d, elapsedMs=%d%n",
                1_001,
                optimized.queryCount,
                optimized.elapsedMs);

        assertTrue(optimized.queryCount <= 5,
                "optimized dashboard path should keep a fixed query budget for a large fixture");
    }

    private void legacyDashboardStatistics(Long kindergartenId) {
        kidRepository.countByClassroomKindergartenId(kindergartenId);
        memberRepository.countByKindergartenIdAndRole(kindergartenId, MemberRole.TEACHER);
        memberRepository.countByKindergartenIdAndRole(kindergartenId, MemberRole.PARENT);

        legacyAttendanceRate(kindergartenId, 7);
        legacyAttendanceRate(kindergartenId, 30);
        legacyAnnouncementReadRate(kindergartenId);

        announcementRepository.countByKindergartenIdAndDeletedAtIsNull(kindergartenId);
        attendanceRepository.countByKidClassroomKindergartenIdAndDate(kindergartenId, LocalDate.now());
    }

    private double legacyAttendanceRate(Long kindergartenId, int days) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Kid> kids = kidRepository.findByClassroomKindergartenId(kindergartenId);
        if (kids.isEmpty()) {
            return 0.0;
        }

        long totalPossibleAttendance = (long) kids.size() * days;
        if (totalPossibleAttendance == 0) {
            return 0.0;
        }

        long totalPresent = attendanceRepository.countByKidClassroomKindergartenIdAndDateBetweenAndStatus(
                kindergartenId,
                startDate,
                endDate,
                AttendanceStatus.PRESENT
        );
        long totalLate = attendanceRepository.countByKidClassroomKindergartenIdAndDateBetweenAndStatus(
                kindergartenId,
                startDate,
                endDate,
                AttendanceStatus.LATE
        );

        return ((totalPresent + totalLate) * 100.0) / totalPossibleAttendance;
    }

    private double legacyAnnouncementReadRate(Long kindergartenId) {
        List<Announcement> announcements = announcementRepository.findByKindergartenIdAndDeletedAtIsNull(kindergartenId);
        if (announcements.isEmpty()) {
            return 0.0;
        }

        int totalViewCount = announcements.stream()
                .mapToInt(Announcement::getViewCount)
                .sum();
        long totalMembers = memberRepository.countByKindergartenIdAndDeletedAtIsNull(kindergartenId);
        if (totalMembers == 0 || totalViewCount == 0) {
            return 0.0;
        }

        return (totalViewCount * 100.0) / totalMembers;
    }

    private Measurement measure(Statistics statistics, Runnable action) {
        entityManager.clear();
        statistics.clear();
        long start = System.nanoTime();
        action.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long queryCount = statistics.getPrepareStatementCount();
        return new Measurement(queryCount, elapsedMs);
    }

    private record Measurement(long queryCount, long elapsedMs) {
    }

    private void clearDashboardCache() {
        Cache cache = cacheManager.getCache("dashboardStatistics");
        if (cache != null) {
            cache.clear();
        }
    }
}
