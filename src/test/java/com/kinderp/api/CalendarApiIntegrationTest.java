package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.calendar.entity.CalendarEvent;
import com.kinderp.domain.calendar.entity.CalendarEventType;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;
import com.kinderp.domain.calendar.repository.CalendarEventRepository;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("캘린더 API 테스트")
@Tag("integration")
class CalendarApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Test
    @DisplayName("반복 일정 조회 - 주간 반복 occurrence 확장 성공")
    void getRecurringClassroomEvents_Success() throws Exception {
        String requestBody = """
                {
                    "title": "주간 상담",
                    "description": "매주 상담 일정",
                    "startDateTime": "2026-03-03T10:00:00",
                    "endDateTime": "2026-03-03T11:00:00",
                    "eventType": "MEETING",
                    "scopeType": "CLASSROOM",
                    "classroomId": %d,
                    "isAllDay": false,
                    "repeatType": "WEEKLY",
                    "repeatEndDate": "2026-03-31"
                }
                """.formatted(classroom.getId());

        mockMvc.perform(post("/api/v1/calendar/events")
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.repeatType").value("WEEKLY"));

        mockMvc.perform(get("/api/v1/calendar/events")
                        .with(authenticated(teacherMember))
                        .param("startDate", "2026-03-17")
                        .param("endDate", "2026-03-17"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("주간 상담"))
                .andExpect(jsonPath("$.data[0].startDateTime").value("2026-03-17T10:00:00"))
                .andExpect(jsonPath("$.data[0].endDateTime").value("2026-03-17T11:00:00"))
                .andExpect(jsonPath("$.data[0].scopeType").value("CLASSROOM"));
    }

    @Test
    @DisplayName("유치원 전체 일정 생성 - 원장 성공, 교사 실패")
    void createKindergartenEvent_AccessMatrix() throws Exception {
        String requestBody = """
                {
                    "title": "전체 행사",
                    "description": "유치원 전체 일정",
                    "startDateTime": "2026-03-21T10:00:00",
                    "endDateTime": "2026-03-21T11:00:00",
                    "eventType": "EVENT",
                    "scopeType": "KINDERGARTEN",
                    "isAllDay": false,
                    "repeatType": "NONE"
                }
                """;

        mockMvc.perform(post("/api/v1/calendar/events")
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scopeType").value("KINDERGARTEN"));

        mockMvc.perform(post("/api/v1/calendar/events")
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CA002"));
    }

    @Test
    @DisplayName("반 일정 조회 권한 - 담임/학부모 성공, 원장 상세 조회 실패")
    void getClassroomEvent_AccessMatrix() throws Exception {
        CalendarEvent event = calendarEventRepository.save(CalendarEvent.create(
                kindergarten,
                classroom,
                teacherMember,
                "반 상담",
                "반 일정",
                LocalDateTime.of(2026, 3, 22, 10, 0),
                LocalDateTime.of(2026, 3, 22, 11, 0),
                CalendarEventType.MEETING,
                CalendarScopeType.CLASSROOM,
                false,
                null,
                RepeatType.NONE,
                null
        ));

        mockMvc.perform(get("/api/v1/calendar/events/{id}", event.getId())
                        .with(authenticated(teacherMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scopeType").value("CLASSROOM"));

        mockMvc.perform(get("/api/v1/calendar/events/{id}", event.getId())
                        .with(authenticated(parentMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scopeType").value("CLASSROOM"));

        mockMvc.perform(get("/api/v1/calendar/events/{id}", event.getId())
                        .with(authenticated(principalMember)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CA002"));
    }

    @Test
    @DisplayName("개인 일정 관리 권한 - 본인 수정 성공, 다른 사용자 삭제 실패")
    void managePersonalEvent_AccessMatrix() throws Exception {
        CalendarEvent event = calendarEventRepository.save(CalendarEvent.create(
                kindergarten,
                null,
                teacherMember,
                "개인 일정",
                "교사 개인 일정",
                LocalDateTime.of(2026, 3, 23, 10, 0),
                LocalDateTime.of(2026, 3, 23, 11, 0),
                CalendarEventType.MEETING,
                CalendarScopeType.PERSONAL,
                false,
                null,
                RepeatType.NONE,
                null
        ));

        String updateBody = """
                {
                    "title": "개인 일정 수정",
                    "description": "교사 개인 일정 수정",
                    "startDateTime": "2026-03-23T10:30:00",
                    "endDateTime": "2026-03-23T11:30:00",
                    "eventType": "MEETING",
                    "scopeType": "PERSONAL",
                    "isAllDay": false,
                    "repeatType": "NONE"
                }
                """;

        mockMvc.perform(put("/api/v1/calendar/events/{id}", event.getId())
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("개인 일정 수정"));

        mockMvc.perform(delete("/api/v1/calendar/events/{id}", event.getId())
                        .with(authenticated(parentMember))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CA002"));
    }

    @Test
    @DisplayName("반 일정 생성 - 학부모 실패")
    void createClassroomEvent_Fail_Parent() throws Exception {
        String requestBody = """
                {
                    "title": "학부모 반 일정",
                    "description": "학부모는 반 일정을 생성할 수 없다",
                    "startDateTime": "2026-03-24T10:00:00",
                    "endDateTime": "2026-03-24T11:00:00",
                    "eventType": "MEETING",
                    "scopeType": "CLASSROOM",
                    "classroomId": %d,
                    "isAllDay": false,
                    "repeatType": "NONE"
                }
                """.formatted(classroom.getId());

        mockMvc.perform(post("/api/v1/calendar/events")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CA002"));
    }

    @Test
    @DisplayName("유치원 전체 일정 조회 - 학부모 성공")
    void getKindergartenEvent_Success_Parent() throws Exception {
        CalendarEvent event = calendarEventRepository.save(CalendarEvent.create(
                kindergarten,
                null,
                principalMember,
                "봄 소풍 안내",
                "유치원 전체 일정",
                LocalDateTime.of(2026, 3, 20, 9, 0),
                LocalDateTime.of(2026, 3, 20, 12, 0),
                CalendarEventType.EVENT,
                CalendarScopeType.KINDERGARTEN,
                false,
                "한강공원",
                RepeatType.NONE,
                null
        ));

        mockMvc.perform(get("/api/v1/calendar/events")
                        .with(authenticated(parentMember))
                        .param("startDate", "2026-03-20")
                        .param("endDate", "2026-03-20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(event.getId()))
                .andExpect(jsonPath("$.data[0].scopeType").value("KINDERGARTEN"));

        mockMvc.perform(get("/api/v1/calendar/events/{id}", event.getId())
                        .with(authenticated(parentMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("봄 소풍 안내"));
    }

    @Test
    @DisplayName("일정 목록 조회 - 366일 초과 범위는 실패")
    void getEvents_Fail_QueryRangeTooLong() throws Exception {
        mockMvc.perform(get("/api/v1/calendar/events")
                        .with(authenticated(principalMember))
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2027-01-03"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("일정 상세 조회 - 다른 유치원 일정은 차단")
    void getEvent_Fail_OtherKindergartenMember() throws Exception {
        Kindergarten otherKindergarten = testData.createKindergarten();
        Member otherPrincipal = createMemberInKindergarten(
                "other-principal@test.com",
                "다른 원장",
                MemberRole.PRINCIPAL,
                otherKindergarten
        );
        Classroom otherClassroom = testData.createClassroom(otherKindergarten);
        Member otherTeacher = createMemberInKindergarten(
                "other-teacher@test.com",
                "다른 교사",
                MemberRole.TEACHER,
                otherKindergarten
        );
        otherClassroom.assignTeacher(otherTeacher);
        classroomRepository.save(otherClassroom);

        CalendarEvent otherEvent = calendarEventRepository.save(CalendarEvent.create(
                otherKindergarten,
                null,
                otherPrincipal,
                "타 유치원 행사",
                "접근 차단 대상",
                LocalDateTime.of(2026, 3, 25, 10, 0),
                LocalDateTime.of(2026, 3, 25, 11, 0),
                CalendarEventType.EVENT,
                CalendarScopeType.KINDERGARTEN,
                false,
                null,
                RepeatType.NONE,
                null
        ));

        mockMvc.perform(get("/api/v1/calendar/events/{id}", otherEvent.getId())
                        .with(authenticated(teacherMember)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CA002"));
    }

    @Test
    @DisplayName("반복 일정 생성 - repeatEndDate 누락 시 실패")
    void createRecurringEvent_Fail_MissingRepeatEndDate() throws Exception {
        String requestBody = """
                {
                    "title": "주간 상담",
                    "description": "매주 상담 일정",
                    "startDateTime": "2026-03-03T10:00:00",
                    "endDateTime": "2026-03-03T11:00:00",
                    "eventType": "MEETING",
                    "scopeType": "CLASSROOM",
                    "classroomId": %d,
                    "isAllDay": false,
                    "repeatType": "WEEKLY"
                }
                """.formatted(classroom.getId());

        mockMvc.perform(post("/api/v1/calendar/events")
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
