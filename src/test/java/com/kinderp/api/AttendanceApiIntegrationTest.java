package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 출결 API 통합 테스트
 */
@DisplayName("출결 API 테스트")
@Tag("integration")
class AttendanceApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ClassroomRepository classroomRepository;

    @AfterEach
    void cleanUp() {
        testData.cleanup();
    }

    @Nested
    @DisplayName("출석 등록 API")
    class CreateAttendanceTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("출석 등록 - 성공 (원장)")
        void createAttendance_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "kidId": 1,
                        "date": "2025-01-13",
                        "status": "PRESENT",
                        "dropOffTime": "09:00",
                        "pickUpTime": "16:00"
                    }
                    """;

            mockMvc.perform(post("/api/v1/attendance")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("출석 등록 - 성공 (교사)")
        void createAttendance_Success_Teacher() throws Exception {
            String requestBody = """
                    {
                        "kidId": 1,
                        "date": "2025-01-13",
                        "status": "PRESENT",
                        "dropOffTime": "09:00",
                        "pickUpTime": "16:00"
                    }
                    """;

            mockMvc.perform(post("/api/v1/attendance")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("출석 등록 - 실패 (학부모는 권한 없음)")
        void createAttendance_Fail_Parent() throws Exception {
            String requestBody = """
                    {
                        "kidId": 1,
                        "date": "2025-01-13",
                        "status": "PRESENT"
                    }
                    """;

            mockMvc.perform(post("/api/v1/attendance")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("출석 등록 - 실패 (다른 유치원 원생에는 등록 불가)")
        void createAttendance_Fail_DifferentKindergartenKid() throws Exception {
            var otherKindergarten = testData.createKindergarten();
            var otherTeacher = createMemberInKindergarten(
                    "attendance-other-teacher@test.com",
                    "다른 유치원 교사",
                    MemberRole.TEACHER,
                    otherKindergarten
            );
            var otherClassroom = testData.createClassroom(otherKindergarten);
            otherClassroom.assignTeacher(otherTeacher);
            classroomRepository.save(otherClassroom);
            var otherKid = testData.createKid(otherClassroom);

            String requestBody = """
                    {
                        "kidId": %d,
                        "date": "2025-01-13",
                        "status": "PRESENT"
                    }
                    """.formatted(otherKid.getId());

            mockMvc.perform(post("/api/v1/attendance")
                            .with(authenticated(teacherMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("AP007"));
        }
    }

    @Nested
    @DisplayName("출석 조회 API")
    class GetAttendanceTest {

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("출석 단건 조회 - 성공")
        void getAttendance_Success() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생별 날짜 출석 조회 - 성공")
        void getAttendanceByKidAndDate_Success() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/1")
                            .param("date", "2025-01-13"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("반별 일별 출석 현황 조회 - 성공")
        void getDailyAttendance_Success() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/daily")
                            .param("classroomId", "1")
                            .param("date", "2025-01-13"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("홈 출결 요약 - 교사 담당 반 범위만 조회")
        void getDashboardSummary_Success_Teacher() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/dashboard-summary")
                            .param("startDate", "2025-01-13")
                            .param("endDate", "2025-01-17"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.expectedCount").isNumber())
                    .andExpect(jsonPath("$.data.attendanceRate").isNumber());
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("홈 출결 요약 - 학부모 자녀 범위만 조회")
        void getDashboardSummary_Success_Parent() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/dashboard-summary")
                            .param("startDate", "2025-01-13")
                            .param("endDate", "2025-01-17"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.expectedCount").isNumber());
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("홈 출결 요약 - 기간이 31일을 초과하면 실패")
        void getDashboardSummary_Fail_TooLongRange() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/dashboard-summary")
                            .param("startDate", "2025-01-01")
                            .param("endDate", "2025-02-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("반별 일별 출석 현황 조회 - 실패 (반 ID 누락)")
        void getDailyAttendance_Fail_MissingClassroomId() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/daily")
                            .param("date", "2025-01-13"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생별 월간 출석 목록 조회 - 성공")
        void getMonthlyAttendances_Success() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/1/monthly")
                            .param("year", "2025")
                            .param("month", "1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생별 월간 출석 목록 조회 - 실패 (월 범위 오류)")
        void getMonthlyAttendances_Fail_InvalidMonth() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/1/monthly")
                            .param("year", "2025")
                            .param("month", "13"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생별 월간 출석 목록 조회 - 실패 (필수 파라미터 누락)")
        void getMonthlyAttendances_Fail_MissingYear() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/1/monthly")
                            .param("month", "1"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C001"))
                    .andExpect(jsonPath("$.data.year").value("필수 요청 파라미터입니다"));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생별 월간 출석 목록 조회 - 실패 (파라미터 타입 오류)")
        void getMonthlyAttendances_Fail_InvalidYearType() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/1/monthly")
                            .param("year", "invalid")
                            .param("month", "1"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C001"))
                    .andExpect(jsonPath("$.data.year").value("요청 파라미터 타입이 올바르지 않습니다"));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("월간 출석 통계 조회 - 성공")
        void getMonthlyStatistics_Success() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/1/statistics")
                            .param("year", "2025")
                            .param("month", "1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("출석 수정 API")
    class UpdateAttendanceTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("출석 수정 - 성공 (원장)")
        void updateAttendance_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "kidId": 1,
                        "date": "2025-01-13",
                        "status": "LATE",
                        "dropOffTime": "09:30",
                        "pickUpTime": "16:00"
                    }
                    """;

            mockMvc.perform(put("/api/v1/attendance/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("특별 출석 처리 API")
    class SpecialAttendanceTest {

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("등원 기록 - 성공")
        void recordDropOff_Success() throws Exception {
            String requestBody = """
                    {
                        "guardianName": "엄마",
                        "guardianRelation": "모"
                    }
                    """;

            mockMvc.perform(post("/api/v1/attendance/kid/1/drop-off")
                            .with(csrf())
                            .param("date", "2025-01-13")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("하원 기록 - 성공")
        void recordPickUp_Success() throws Exception {
            String requestBody = """
                    {
                        "guardianName": "아빠",
                        "guardianRelation": "부"
                    }
                    """;

            mockMvc.perform(post("/api/v1/attendance/kid/1/pick-up")
                            .with(csrf())
                            .param("date", "2025-01-13")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("결석 처리 - 성공")
        void markAbsent_Success() throws Exception {
            mockMvc.perform(post("/api/v1/attendance/kid/1/absent")
                            .with(csrf())
                            .param("date", "2025-01-13")
                            .param("note", "아파서 결석"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("지각 처리 - 성공")
        void markLate_Success() throws Exception {
            mockMvc.perform(post("/api/v1/attendance/kid/1/late")
                            .with(csrf())
                            .param("date", "2025-01-13")
                            .param("dropOffTime", "09:30"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("조퇴 처리 - 성공")
        void markEarlyLeave_Success() throws Exception {
            mockMvc.perform(post("/api/v1/attendance/kid/1/early-leave")
                            .with(csrf())
                            .param("date", "2025-01-13")
                            .param("pickUpTime", "14:00"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("병결 처리 - 성공")
        void markSickLeave_Success() throws Exception {
            mockMvc.perform(post("/api/v1/attendance/kid/1/sick-leave")
                            .with(csrf())
                            .param("date", "2025-01-13")
                            .param("note", "감기로 병결"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("출석 삭제 API")
    class DeleteAttendanceTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("출석 삭제 - 성공 (원장)")
        void deleteAttendance_Success_Principal() throws Exception {
            mockMvc.perform(delete("/api/v1/attendance/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("출석 삭제 - 실패 (학부모는 권한 없음)")
        void deleteAttendance_Fail_Parent() throws Exception {
            mockMvc.perform(delete("/api/v1/attendance/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("학부모의 반 단위 출결 접근 제한")
    class ParentClassroomAttendanceAccessTest {

        @Test
        @DisplayName("반별 일별 출석 현황 - 학부모는 자기 아이가 있는 반이라도 거부")
        void getDailyAttendance_Fail_Parent() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/daily")
                            .param("classroomId", String.valueOf(classroom.getId()))
                            .param("date", "2025-01-13")
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("반별 일별 출석 현황 - 같은 유치원 교사는 성공하고 원생 출결을 반환")
        void getDailyAttendance_Success_Teacher() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/daily")
                            .param("classroomId", String.valueOf(classroom.getId()))
                            .param("date", "2025-01-13")
                            .with(authenticated(teacherMember)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("반별 일별 출석 화면 - 학부모는 거부")
        void dailyAttendancePage_Fail_Parent() throws Exception {
            mockMvc.perform(get("/attendance/daily")
                            .param("classroomId", String.valueOf(classroom.getId()))
                            .param("date", "2025-01-13")
                            .with(authenticated(parentMember)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("내 아이 월간 출석 - 학부모는 계속 조회 가능")
        void getKidMonthly_Success_Parent() throws Exception {
            mockMvc.perform(get("/api/v1/attendance/kid/{kidId}/monthly", kid.getId())
                            .param("year", "2025")
                            .param("month", "1")
                            .with(authenticated(parentMember)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("반 일괄 출결 API")
    class BulkAttendanceTest {

        @Test
        @DisplayName("kidIds를 생략하면 반 전체 원생의 출결을 일괄 처리하고, 기존 출결은 갱신한다")
        void bulkUpdate_Success_WholeClassroom() throws Exception {
            // setUp의 원생은 2025-01-13에 이미 PRESENT 출결이 있다.
            var secondKid = kidRepository.save(com.kinderp.domain.kid.entity.Kid.create(
                    classroom, "두번째 원생", java.time.LocalDate.of(2020, 2, 2),
                    com.kinderp.domain.kid.entity.Gender.FEMALE, java.time.LocalDate.of(2024, 1, 1)));

            mockMvc.perform(post("/api/v1/attendance/bulk")
                            .with(authenticated(teacherMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"classroomId": %d, "date": "2025-01-13", "status": "ABSENT", "note": "일괄 결석"}
                                    """.formatted(classroom.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.updatedCount").value(2));

            assertThat(attendanceRepository.findByKidIdAndDate(kid.getId(), java.time.LocalDate.of(2025, 1, 13)))
                    .get().extracting(a -> a.getStatus()).isEqualTo(com.kinderp.domain.attendance.entity.AttendanceStatus.ABSENT);
            assertThat(attendanceRepository.findByKidIdAndDate(secondKid.getId(), java.time.LocalDate.of(2025, 1, 13)))
                    .isPresent();
        }

        @Test
        @DisplayName("다른 반 원생이 섞여 있으면 거부하고 아무것도 저장하지 않는다")
        void bulkUpdate_Fail_KidFromOtherClassroom() throws Exception {
            var otherKindergarten = testData.createKindergarten();
            var otherClassroom = testData.createClassroom(otherKindergarten);
            var otherKid = testData.createKid(otherClassroom);

            mockMvc.perform(post("/api/v1/attendance/bulk")
                            .with(authenticated(teacherMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"classroomId": %d, "date": "2025-02-03", "status": "PRESENT", "kidIds": [%d, %d]}
                                    """.formatted(classroom.getId(), kid.getId(), otherKid.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("존재하지 않는 원생이 포함되면 404를 반환한다")
        void bulkUpdate_Fail_UnknownKid() throws Exception {
            mockMvc.perform(post("/api/v1/attendance/bulk")
                            .with(authenticated(teacherMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"classroomId": %d, "date": "2025-02-03", "status": "PRESENT", "kidIds": [%d, 999999]}
                                    """.formatted(classroom.getId(), kid.getId())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("다른 유치원 교사는 일괄 출결을 처리할 수 없다")
        void bulkUpdate_Fail_OtherKindergartenTeacher() throws Exception {
            var otherKindergarten = testData.createKindergarten();
            var otherTeacher = createMemberInKindergarten(
                    "bulk-other-teacher@test.com", "다른 교사", MemberRole.TEACHER, otherKindergarten);

            mockMvc.perform(post("/api/v1/attendance/bulk")
                            .with(authenticated(otherTeacher))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"classroomId": %d, "date": "2025-02-03", "status": "PRESENT"}
                                    """.formatted(classroom.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("학부모는 일괄 출결을 처리할 수 없다")
        void bulkUpdate_Fail_Parent() throws Exception {
            mockMvc.perform(post("/api/v1/attendance/bulk")
                            .with(authenticated(parentMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"classroomId": %d, "date": "2025-02-03", "status": "PRESENT"}
                                    """.formatted(classroom.getId())))
                    .andExpect(status().isForbidden());
        }
    }
}
