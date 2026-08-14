package com.erp.api;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.attendance.entity.AttendanceChangeRequestStatus;
import com.erp.domain.attendance.entity.AttendanceStatus;
import com.erp.domain.attendance.repository.AttendanceChangeRequestRepository;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("출결 변경 요청 API 테스트")
@Tag("integration")
class AttendanceChangeRequestApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AttendanceChangeRequestRepository attendanceChangeRequestRepository;

    @Test
    @DisplayName("학부모는 자기 자녀의 출결 변경 요청을 생성할 수 있다")
    void createAttendanceChangeRequest_Success() throws Exception {
        String requestBody = """
                {
                    "kidId": 1,
                    "date": "2025-01-14",
                    "status": "ABSENT",
                    "note": "병원 진료"
                }
                """;

        mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var requests = attendanceChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(parentMember.getId());
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getRequestedStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(requests.get(0).getStatus()).isEqualTo(AttendanceChangeRequestStatus.PENDING);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 재전송하면 기존 출결 요청 ID를 반환한다")
    void createAttendanceChangeRequest_IsIdempotent() throws Exception {
        String requestBody = """
                {
                    "kidId": 1,
                    "date": "2025-01-16",
                    "status": "ABSENT",
                    "note": "병원 진료"
                }
                """;

        long firstId = objectMapper.readTree(mockMvc.perform(post("/api/v1/attendance-requests")
                                .with(authenticated(parentMember))
                                .with(csrf())
                                .header("Idempotency-Key", "attendance-retry-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .asLong();

        mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .header("Idempotency-Key", "attendance-retry-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(firstId));

        assertThat(attendanceChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(parentMember.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Idempotency-Key를 다른 payload에 재사용하면 거부한다")
    void createAttendanceChangeRequest_Fail_WhenIdempotencyKeyPayloadDiffers() throws Exception {
        createAttendanceChangeRequestWithKey("attendance-reuse-001", "2025-01-17", "ABSENT");

        mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .header("Idempotency-Key", "attendance-reuse-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "kidId": 1,
                                    "date": "2025-01-17",
                                    "status": "PRESENT",
                                    "note": "다른 payload"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AT008"));
    }

    @Test
    @DisplayName("Idempotency-Key가 100자를 초과하면 거부한다")
    void createAttendanceChangeRequest_Fail_WhenIdempotencyKeyTooLong() throws Exception {
        mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .header("Idempotency-Key", "k".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "kidId": 1,
                                    "date": "2025-01-18",
                                    "status": "ABSENT",
                                    "note": "잘못된 키"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("학부모는 다른 유치원 원생의 출결 변경 요청을 생성할 수 없다")
    void createAttendanceChangeRequest_Fail_DifferentKindergartenKid() throws Exception {
        Kindergarten otherKindergarten = testData.createKindergarten();
        var otherTeacher = createMemberInKindergarten(
                "attendance-request-other-teacher@test.com",
                "다른교사",
                MemberRole.TEACHER,
                otherKindergarten
        );
        var otherClassroom = testData.createClassroom(otherKindergarten);
        otherClassroom.assignTeacher(otherTeacher);
        classroomRepository.saveAndFlush(otherClassroom);
        var otherKid = testData.createKid(otherClassroom);

        String requestBody = """
                {
                    "kidId": %d,
                    "date": "2025-01-14",
                    "status": "ABSENT",
                    "note": "외부 원생"
                }
                """.formatted(otherKid.getId());

        mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AT007"));
    }

    @Test
    @DisplayName("교사는 대기 중인 출결 변경 요청을 승인할 수 있다")
    void approveAttendanceChangeRequest_Success() throws Exception {
        long requestId = createAttendanceChangeRequest();

        mockMvc.perform(post("/api/v1/attendance-requests/{id}/approve", requestId)
                        .with(authenticated(teacherMember))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var request = attendanceChangeRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(AttendanceChangeRequestStatus.APPROVED);
        assertThat(request.getAttendanceId()).isNotNull();
        assertThat(attendanceRepository.findByKidIdAndDate(kid.getId(), request.getDate()).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    @DisplayName("교사는 대기 중인 출결 변경 요청을 거절할 수 있다")
    void rejectAttendanceChangeRequest_Success() throws Exception {
        long requestId = createAttendanceChangeRequest();

        mockMvc.perform(post("/api/v1/attendance-requests/{id}/reject", requestId)
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "증빙 서류가 필요합니다"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var request = attendanceChangeRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(AttendanceChangeRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("증빙 서류가 필요합니다");
        assertThat(attendanceRepository.findByKidIdAndDate(kid.getId(), request.getDate())).isEmpty();
    }

    @Test
    @DisplayName("다른 유치원 교사는 출결 요청을 승인할 수 없다")
    void approveAttendanceChangeRequest_Fail_DifferentKindergartenTeacher() throws Exception {
        long requestId = createAttendanceChangeRequest();

        Kindergarten otherKindergarten = testData.createKindergarten();
        var otherTeacher = createMemberInKindergarten(
                "attendance-request-reviewer@test.com",
                "외부교사",
                MemberRole.TEACHER,
                otherKindergarten
        );

        mockMvc.perform(post("/api/v1/attendance-requests/{id}/approve", requestId)
                        .with(authenticated(otherTeacher))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP007"));
    }

    @Test
    @DisplayName("같은 원생과 날짜에 대기 중인 출결 요청이 있으면 중복 생성할 수 없다")
    void createAttendanceChangeRequest_Fail_WhenPendingAlreadyExists() throws Exception {
        createAttendanceChangeRequest();

        String requestBody = """
                {
                    "kidId": 1,
                    "date": "2025-01-14",
                    "status": "ABSENT",
                    "note": "중복 요청"
                }
                """;

        mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AT005"));
    }

    private long createAttendanceChangeRequest() throws Exception {
        String requestBody = """
                {
                    "kidId": 1,
                    "date": "2025-01-14",
                    "status": "ABSENT",
                    "note": "병원 진료"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").asLong();
    }

    private long createAttendanceChangeRequestWithKey(String key, String date, String status) throws Exception {
        String response = mockMvc.perform(post("/api/v1/attendance-requests")
                        .with(authenticated(parentMember))
                        .with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "kidId": 1,
                                    "date": "%s",
                                    "status": "%s",
                                    "note": "멱등성 테스트"
                                }
                                """.formatted(date, status)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").asLong();
    }
}
