package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("교사 지원 API 테스트")
@Tag("integration")
class KindergartenApplicationApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("교사 지원 승인 - 성공")
    void approveKindergartenApplication_Success() throws Exception {
        Member applicantTeacher = testData.createTestMember("apply-teacher@test.com", "신규교사", MemberRole.TEACHER, "test1234");

        String applyBody = """
                {
                    "kindergartenId": 1,
                    "message": "지원합니다"
                }
                """;

        MvcResult applyResult = mockMvc.perform(post("/api/v1/kindergarten-applications")
                        .with(user(new CustomUserDetails(applicantTeacher)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        long applicationId = objectMapper.readTree(applyResult.getResponse().getContentAsString())
                .path("data")
                .asLong();

        mockMvc.perform(put("/api/v1/kindergarten-applications/{id}/approve", applicationId)
                        .with(user(new CustomUserDetails(principalMember)))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Member updatedTeacher = memberRepository.findById(applicantTeacher.getId()).orElseThrow();
        assertTrue(updatedTeacher.getKindergarten() != null);
    }

    @Test
    @DisplayName("교사 지원 신청 - 실패 (학부모 권한 없음)")
    void applyKindergartenApplication_Fail_Parent() throws Exception {
        String applyBody = """
                {
                    "kindergartenId": 1,
                    "message": "학부모 권한 테스트"
                }
                """;

        mockMvc.perform(post("/api/v1/kindergarten-applications")
                        .with(user(new CustomUserDetails(parentMember)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("교사 지원 상세 조회 - 지원한 교사 본인은 조회할 수 있다")
    void getKindergartenApplication_Success_ApplicantTeacher() throws Exception {
        Member applicantTeacher = testData.createTestMember("detail-teacher@test.com", "지원교사", MemberRole.TEACHER, "test1234");
        long applicationId = applyKindergartenApplication(applicantTeacher, "상세 조회 테스트");

        mockMvc.perform(get("/api/v1/kindergarten-applications/{id}", applicationId)
                        .with(user(new CustomUserDetails(applicantTeacher))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(applicationId));
    }

    @Test
    @DisplayName("교사 지원 상세 조회 - 같은 유치원 원장은 조회할 수 있다")
    void getKindergartenApplication_Success_PrincipalReviewer() throws Exception {
        Member applicantTeacher = testData.createTestMember("detail-principal-teacher@test.com", "지원교사2", MemberRole.TEACHER, "test1234");
        long applicationId = applyKindergartenApplication(applicantTeacher, "원장 조회 테스트");

        mockMvc.perform(get("/api/v1/kindergarten-applications/{id}", applicationId)
                        .with(user(new CustomUserDetails(principalMember))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(applicationId));
    }

    @Test
    @DisplayName("교사 지원 상세 조회 - 같은 유치원의 다른 교사는 조회할 수 없다")
    void getKindergartenApplication_Fail_SameKindergartenOtherTeacher() throws Exception {
        Member applicantTeacher = testData.createTestMember("detail-owner-teacher@test.com", "지원교사3", MemberRole.TEACHER, "test1234");
        long applicationId = applyKindergartenApplication(applicantTeacher, "다른 교사 차단");

        mockMvc.perform(get("/api/v1/kindergarten-applications/{id}", applicationId)
                        .with(user(new CustomUserDetails(teacherMember))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP005"));
    }

    private long applyKindergartenApplication(Member applicantTeacher, String message) throws Exception {
        String applyBody = """
                {
                    "kindergartenId": 1,
                    "message": "%s"
                }
                """.formatted(message);

        MvcResult applyResult = mockMvc.perform(post("/api/v1/kindergarten-applications")
                        .with(user(new CustomUserDetails(applicantTeacher)))
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
