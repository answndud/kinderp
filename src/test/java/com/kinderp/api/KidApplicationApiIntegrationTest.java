package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kidapplication.entity.ApplicationStatus;
import com.kinderp.domain.kidapplication.repository.KidApplicationRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("입학 신청 API 테스트")
@Tag("integration")
class KidApplicationApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KidApplicationRepository kidApplicationRepository;

    @Test
    @DisplayName("입학 신청 승인 - 성공 (관계값 반영)")
    void approveKidApplication_Success_WithRelationship() throws Exception {
        Member applicant = testData.createTestMember("apply-parent@test.com", "신청학부모", MemberRole.PARENT, "test1234");

        long applicationId = applyKidApplication(applicant, 1L, "신청원아");

        String approveBody = """
                {
                    "classroomId": 1,
                    "relationship": "MOTHER"
                }
                """;

        mockMvc.perform(put("/api/v1/kid-applications/{id}/approve", applicationId)
                        .with(user(new CustomUserDetails(principalMember)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(parentKidRepository.findAll().stream()
                .anyMatch(parentKid -> parentKid.getParent().getId().equals(applicant.getId())
                        && "MOTHER".equals(parentKid.getRelationship().name())));
    }

    @Test
    @DisplayName("입학 신청 승인 - 실패 (학부모 권한 없음)")
    void approveKidApplication_Fail_Parent() throws Exception {
        Member applicant = testData.createTestMember("apply-parent2@test.com", "신청학부모2", MemberRole.PARENT, "test1234");

        long applicationId = applyKidApplication(applicant, 1L, "권한테스트원아");

        String approveBody = """
                {
                    "classroomId": 1
                }
                """;

        mockMvc.perform(put("/api/v1/kid-applications/{id}/approve", applicationId)
                        .with(user(new CustomUserDetails(applicant)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("정원이 가득 찬 반은 대기열 등록으로 처리할 수 있다")
    void waitlistKidApplication_Success_WhenClassroomIsFull() throws Exception {
        classroom.update("테스트반", "5세", 1);
        Member applicant = testData.createTestMember("waitlist-parent@test.com", "대기학부모", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "대기원아");

        String requestBody = """
                {
                    "classroomId": %d,
                    "decisionNote": "정원 만석으로 대기열 등록"
                }
                """.formatted(classroom.getId());

        mockMvc.perform(put("/api/v1/kid-applications/{id}/waitlist", applicationId)
                        .with(user(new CustomUserDetails(principalMember)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var application = kidApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WAITLISTED);
        assertThat(application.getAssignedClassroom().getId()).isEqualTo(classroom.getId());
    }

    @Test
    @DisplayName("입학 제안 후 학부모가 수락하면 원생과 부모 연결이 생성된다")
    void acceptOfferedKidApplication_Success() throws Exception {
        Member applicant = testData.createTestMember("offer-parent@test.com", "제안학부모", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "제안원아");

        String offerBody = """
                {
                    "classroomId": %d,
                    "decisionNote": "좌석 1석 확보"
                }
                """.formatted(classroom.getId());

        mockMvc.perform(put("/api/v1/kid-applications/{id}/offer", applicationId)
                        .with(user(new CustomUserDetails(teacherMember)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String acceptBody = """
                {
                    "relationship": "MOTHER"
                }
                """;

        mockMvc.perform(put("/api/v1/kid-applications/{id}/accept-offer", applicationId)
                        .with(user(new CustomUserDetails(applicant)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var application = kidApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getKidId()).isNotNull();
        assertTrue(parentKidRepository.findAll().stream()
                .anyMatch(parentKid -> parentKid.getParent().getId().equals(applicant.getId())
                        && parentKid.getKid().getId().equals(application.getKidId())));
    }

    @Test
    @DisplayName("만료된 입학 제안은 수락할 수 없고 offer expired 상태로 전환된다")
    void acceptOfferedKidApplication_Fail_WhenOfferExpired() throws Exception {
        Member applicant = testData.createTestMember("expired-offer-parent@test.com", "만료학부모", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "만료원아");

        var application = kidApplicationRepository.findById(applicationId).orElseThrow();
        application.offerSeat(classroom, principalMember, LocalDateTime.now().minusMinutes(1), "만료 제안");
        kidApplicationRepository.saveAndFlush(application);

        String acceptBody = """
                {
                    "relationship": "MOTHER"
                }
                """;

        mockMvc.perform(put("/api/v1/kid-applications/{id}/accept-offer", applicationId)
                        .with(user(new CustomUserDetails(applicant)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP010"));

        var expiredApplication = kidApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(expiredApplication.getStatus()).isEqualTo(ApplicationStatus.OFFER_EXPIRED);
        assertThat(expiredApplication.getKidId()).isNull();
    }

    @Test
    @DisplayName("다른 유치원 교사는 입학 제안을 보낼 수 없다")
    void offerKidApplication_Fail_DifferentKindergartenTeacher() throws Exception {
        Member applicant = testData.createTestMember("offer-other-parent@test.com", "외부학부모", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "외부원아");

        var otherKindergarten = testData.createKindergarten();
        var otherTeacher = createMemberInKindergarten(
                "other-kid-application-teacher@test.com",
                "다른교사",
                MemberRole.TEACHER,
                otherKindergarten
        );
        Classroom otherClassroom = testData.createClassroom(otherKindergarten);
        otherClassroom.assignTeacher(otherTeacher);
        classroomRepository.saveAndFlush(otherClassroom);

        String offerBody = """
                {
                    "classroomId": %d
                }
                """.formatted(classroom.getId());

        mockMvc.perform(put("/api/v1/kid-applications/{id}/offer", applicationId)
                        .with(user(new CustomUserDetails(otherTeacher)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offerBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP007"));
    }

    @Test
    @DisplayName("검토 큐 조회는 대기열과 offer 상태를 함께 반환한다")
    void getReviewQueueApplications_Success() throws Exception {
        Member waitlistApplicant = testData.createTestMember("queue-waitlist@test.com", "큐대기", MemberRole.PARENT, "test1234");
        long waitlistId = applyKidApplication(waitlistApplicant, classroom.getId(), "큐대기원아");
        mockMvc.perform(put("/api/v1/kid-applications/{id}/waitlist", waitlistId)
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "classroomId": 1
                                }
                                """))
                .andExpect(status().isOk());

        Member offerApplicant = testData.createTestMember("queue-offer@test.com", "큐제안", MemberRole.PARENT, "test1234");
        long offerId = applyKidApplication(offerApplicant, classroom.getId(), "큐제안원아");
        mockMvc.perform(put("/api/v1/kid-applications/{id}/offer", offerId)
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "classroomId": 1
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/kid-applications/queue")
                        .with(authenticated(principalMember))
                        .param("kindergartenId", String.valueOf(kindergarten.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("WAITLISTED"))
                .andExpect(jsonPath("$.data[1].status").value("OFFERED"));
    }

    @Test
    @DisplayName("입학 신청 상세 조회 - 본인 학부모는 조회할 수 있다")
    void getKidApplication_Success_ApplicantParent() throws Exception {
        Member applicant = testData.createTestMember("kid-detail-parent@test.com", "상세학부모", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "상세원아");

        mockMvc.perform(get("/api/v1/kid-applications/{id}", applicationId)
                        .with(authenticated(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(applicationId))
                .andExpect(jsonPath("$.data.kidName").value("상세원아"));
    }

    @Test
    @DisplayName("입학 신청 상세 조회 - 같은 유치원의 다른 학부모는 조회할 수 없다")
    void getKidApplication_Fail_SameKindergartenOtherParent() throws Exception {
        Member applicant = testData.createTestMember("kid-detail-owner@test.com", "신청자", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "권한원아");

        mockMvc.perform(get("/api/v1/kid-applications/{id}", applicationId)
                        .with(authenticated(parentMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP005"));
    }

    @Test
    @DisplayName("입학 신청 상세 조회 - 같은 유치원의 교사는 조회할 수 있다")
    void getKidApplication_Success_SameKindergartenTeacher() throws Exception {
        Member applicant = testData.createTestMember("kid-detail-staff-parent@test.com", "신청자2", MemberRole.PARENT, "test1234");
        long applicationId = applyKidApplication(applicant, classroom.getId(), "교사조회원아");

        mockMvc.perform(get("/api/v1/kid-applications/{id}", applicationId)
                        .with(authenticated(teacherMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(applicationId));
    }

    @Test
    @DisplayName("입학 신청 - 같은 학부모가 형제 두 명을 같은 유치원에 신청할 수 있다")
    void applyKidApplication_Success_Siblings() throws Exception {
        Member applicant = testData.createTestMember("sibling-parent@test.com", "형제학부모", MemberRole.PARENT, "test1234");

        applyRaw(applicant, 1L, "첫째", "2020-02-02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        applyRaw(applicant, 1L, "둘째", "2022-04-04")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(kidApplicationRepository.findByParentIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicant.getId()))
                .extracting(application -> application.getKidName())
                .containsExactlyInAnyOrder("첫째", "둘째");
    }

    @Test
    @DisplayName("입학 신청 - 같은 원생을 같은 유치원에 진행 중에 다시 신청하면 거부된다")
    void applyKidApplication_Fail_SameKidSameKindergarten() throws Exception {
        Member applicant = testData.createTestMember("dup-parent@test.com", "중복학부모", MemberRole.PARENT, "test1234");
        applyRaw(applicant, 1L, "중복아이", "2020-02-02").andExpect(status().isOk());

        applyRaw(applicant, 1L, "중복아이", "2020-02-02")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AP003"));
    }

    @Test
    @DisplayName("입학 신청 - 같은 원생이 다른 유치원에도 진행 중인 신청이 있으면 거부된다")
    void applyKidApplication_Fail_SameKidOtherKindergarten() throws Exception {
        Member applicant = testData.createTestMember("other-kg-parent@test.com", "다른유치원학부모", MemberRole.PARENT, "test1234");
        var otherKindergarten = testData.createKindergarten();
        applyRaw(applicant, 1L, "이중아이", "2020-02-02").andExpect(status().isOk());

        applyRaw(applicant, otherKindergarten.getId(), "이중아이", "2020-02-02")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AP004"));

        // 다른 원생이라면 다른 유치원에도 신청할 수 있다.
        applyRaw(applicant, otherKindergarten.getId(), "다른아이", "2021-03-03")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("입학 신청 - 취소한 신청은 같은 원생이 다시 신청하면 같은 행이 PENDING으로 재접수된다")
    void applyKidApplication_Success_ReapplyAfterCancel() throws Exception {
        Member applicant = testData.createTestMember("reapply-parent@test.com", "재신청학부모", MemberRole.PARENT, "test1234");
        long firstId = applyKidApplication(applicant, 1L, "재신청아이");

        mockMvc.perform(put("/api/v1/kid-applications/{id}/cancel", firstId)
                        .with(user(new CustomUserDetails(applicant)))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(kidApplicationRepository.findById(firstId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CANCELLED);

        long secondId = applyKidApplication(applicant, 1L, "재신청아이");

        assertThat(secondId).isEqualTo(firstId);
        assertThat(kidApplicationRepository.findById(firstId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("입학 신청 - soft delete된 신청이 키를 점유하지 않아 같은 원생이 새로 신청할 수 있다")
    void applyKidApplication_Success_AfterSoftDelete() throws Exception {
        Member applicant = testData.createTestMember("deleted-parent@test.com", "삭제학부모", MemberRole.PARENT, "test1234");
        long firstId = applyKidApplication(applicant, 1L, "삭제아이");

        var first = kidApplicationRepository.findById(firstId).orElseThrow();
        first.cancel();
        first.softDelete();
        kidApplicationRepository.saveAndFlush(first);

        long secondId = applyKidApplication(applicant, 1L, "삭제아이");

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(kidApplicationRepository.findById(secondId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }

    private ResultActions applyRaw(Member applicant, Long kindergartenId, String kidName, String birthDate) throws Exception {
        String applyBody = """
                {
                    "kindergartenId": %d,
                    "kidName": "%s",
                    "birthDate": "%s",
                    "gender": "FEMALE",
                    "notes": "테스트 신청"
                }
                """.formatted(kindergartenId, kidName, birthDate);

        return mockMvc.perform(post("/api/v1/kid-applications")
                .with(user(new CustomUserDetails(applicant)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(applyBody));
    }

    private long applyKidApplication(Member applicant, Long preferredClassroomId, String kidName) throws Exception {
        String applyBody = """
                {
                    "kindergartenId": 1,
                    "kidName": "%s",
                    "birthDate": "2020-02-02",
                    "gender": "FEMALE",
                    "preferredClassroomId": %d,
                    "notes": "테스트 신청"
                }
                """.formatted(kidName, preferredClassroomId);

        MvcResult applyResult = mockMvc.perform(post("/api/v1/kid-applications")
                        .with(user(new CustomUserDetails(applicant)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        return objectMapper.readTree(applyResult.getResponse().getContentAsString())
                .path("data")
                .asLong();
    }
}
