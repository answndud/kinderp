package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.attendance.entity.Attendance;
import com.kinderp.domain.attendance.entity.AttendanceStatus;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.entity.Relationship;
import com.kinderp.domain.kidapplication.dto.request.AcceptKidApplicationOfferRequest;
import com.kinderp.domain.kidapplication.dto.request.ApproveKidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.KidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.OfferKidApplicationRequest;
import com.kinderp.domain.kidapplication.service.KidApplicationService;
import com.kinderp.domain.kindergartenapplication.dto.request.KindergartenApplicationRequest;
import com.kinderp.domain.kindergartenapplication.service.KindergartenApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("대시보드 API 테스트")
@Tag("integration")
class DashboardApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private KidApplicationService kidApplicationService;

    @Autowired
    private KindergartenApplicationService kindergartenApplicationService;

    @AfterEach
    void clearDashboardCache() {
        dashboardService.evictDashboardStatisticsCache(kindergarten.getId());
    }

    @Test
    @DisplayName("대시보드 통계 조회 - 성공 (원장)")
    void getDashboardStatistics_Success_Principal() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/statistics")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalKids").isNumber())
                .andExpect(jsonPath("$.data.totalTeachers").isNumber())
                .andExpect(jsonPath("$.data.totalParents").isNumber())
                .andExpect(jsonPath("$.data.attendanceRate7Days").isNumber());
    }

    @Test
    @DisplayName("대시보드 통계 조회 - 실패 (학부모는 권한 없음)")
    void getDashboardStatistics_Fail_Parent() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/statistics")
                        .with(authenticated(parentMember)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("대시보드 공지 열람률 - 중복 조회가 아닌 고유 열람 기준으로 집계한다")
    void getDashboardStatistics_UsesUniqueAnnouncementReaders() throws Exception {
        mockMvc.perform(get("/api/v1/announcements/{id}", announcement.getId())
                        .with(authenticated(parentMember)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/announcements/{id}", announcement.getId())
                        .with(authenticated(parentMember)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/announcements/{id}", announcement.getId())
                        .with(authenticated(teacherMember)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/statistics")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAnnouncements").value(1))
                .andExpect(jsonPath("$.data.announcementReadRate").value(closeTo(66.666, 0.2)));
    }

    @Test
    @DisplayName("대시보드 출석률 - 주말과 입소 전 기간을 분모에서 제외한다")
    void getDashboardStatistics_AttendanceRate_ExcludesWeekendAndPreAdmissionDays() throws Exception {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);
        List<LocalDate> schoolDays = schoolDaysBetween(startDate, endDate);

        Kid earlyKid = kidRepository.save(Kid.create(
                classroom,
                "조기 입소 원생",
                LocalDate.of(2020, 2, 1),
                Gender.FEMALE,
                startDate.minusDays(3)
        ));
        LocalDate lateAdmissionDate = schoolDays.get(Math.max(schoolDays.size() - 2, 0));
        Kid lateKid = kidRepository.save(Kid.create(
                classroom,
                "후기 입소 원생",
                LocalDate.of(2020, 3, 1),
                Gender.MALE,
                lateAdmissionDate
        ));

        for (LocalDate schoolDay : schoolDays) {
            attendanceRepository.save(Attendance.create(earlyKid, schoolDay, AttendanceStatus.PRESENT));
            if (!schoolDay.isBefore(lateAdmissionDate)) {
                attendanceRepository.save(Attendance.create(lateKid, schoolDay, AttendanceStatus.PRESENT));
            }
        }

        dashboardService.evictDashboardStatisticsCache(kindergarten.getId());

        mockMvc.perform(get("/api/v1/dashboard/statistics")
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendanceRate7Days").value(closeTo(100.0, 0.01)));
    }

    @Test
    @DisplayName("입학 즉시 승인 시 대시보드 캐시가 무효화되어 원생/학부모 수가 갱신된다")
    void dashboardCacheEvictedOnKidApplicationApprove() {
        var before = dashboardService.getDashboardStatistics(kindergarten);
        var applicant = testData.createTestMember("dashboard-parent@test.com", "대시보드학부모", com.kinderp.domain.member.entity.MemberRole.PARENT, "test1234");

        Long applicationId = kidApplicationService.apply(
                new KidApplicationRequest(
                        kindergarten.getId(),
                        "대시보드원아",
                        LocalDate.of(2020, 2, 2),
                        Gender.FEMALE,
                        classroom.getId(),
                        "대시보드 캐시 검증"
                ),
                applicant.getId()
        );

        kidApplicationService.approve(
                applicationId,
                new ApproveKidApplicationRequest(classroom.getId(), Relationship.MOTHER),
                principalMember.getId()
        );

        var after = dashboardService.getDashboardStatistics(kindergarten);

        assertThat(after.totalKids()).isEqualTo(before.totalKids() + 1);
        assertThat(after.totalParents()).isEqualTo(before.totalParents() + 1);
    }

    @Test
    @DisplayName("입학 offer 수락 시 대시보드 캐시가 무효화되어 원생 수가 갱신된다")
    void dashboardCacheEvictedOnKidApplicationOfferAccept() {
        var applicant = testData.createTestMember("dashboard-offer-parent@test.com", "오퍼학부모", com.kinderp.domain.member.entity.MemberRole.PARENT, "test1234");

        Long applicationId = kidApplicationService.apply(
                new KidApplicationRequest(
                        kindergarten.getId(),
                        "오퍼원아",
                        LocalDate.of(2020, 3, 3),
                        Gender.MALE,
                        classroom.getId(),
                        "offer 수락 캐시 검증"
                ),
                applicant.getId()
        );

        kidApplicationService.offer(
                applicationId,
                new OfferKidApplicationRequest(classroom.getId(), "좌석 확보"),
                principalMember.getId()
        );

        var before = dashboardService.getDashboardStatistics(kindergarten);

        kidApplicationService.acceptOffer(
                applicationId,
                new AcceptKidApplicationOfferRequest(Relationship.FATHER),
                applicant.getId()
        );

        var after = dashboardService.getDashboardStatistics(kindergarten);

        assertThat(after.totalKids()).isEqualTo(before.totalKids() + 1);
    }

    @Test
    @DisplayName("교사 지원 승인 시 대시보드 캐시가 무효화되어 교사 수가 갱신된다")
    void dashboardCacheEvictedOnKindergartenApplicationApprove() {
        var before = dashboardService.getDashboardStatistics(kindergarten);
        var applicantTeacher = testData.createTestMember("dashboard-teacher@test.com", "대시보드교사", com.kinderp.domain.member.entity.MemberRole.TEACHER, "test1234");

        Long applicationId = kindergartenApplicationService.apply(
                applicantTeacher.getId(),
                new KindergartenApplicationRequest(kindergarten.getId(), "대시보드 교사 지원")
        );

        kindergartenApplicationService.approve(applicationId, principalMember.getId());

        var after = dashboardService.getDashboardStatistics(kindergarten);

        assertThat(after.totalTeachers()).isEqualTo(before.totalTeachers() + 1);
    }

    private List<LocalDate> schoolDaysBetween(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY)
                .toList();
    }
}
