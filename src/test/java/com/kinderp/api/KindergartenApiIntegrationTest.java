package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("유치원 API 테스트")
@Tag("integration")
class KindergartenApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("유치원 등록 - 성공 (원장)")
    void createKindergarten_Success_Principal() throws Exception {
        Member unassignedPrincipal = testData.createTestMember(
                "creator@test.com", "신규 원장", MemberRole.PRINCIPAL, "test1234");
        String requestBody = """
                {
                    "name": "새 유치원",
                    "address": "서울시 강남구",
                    "phone": "01012345678",
                    "openTime": "09:00",
                    "closeTime": "18:00"
                }
                """;

        mockMvc.perform(post("/api/v1/kindergartens")
                        .with(authenticated(unassignedPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Member persistedPrincipal = memberRepository.findById(unassignedPrincipal.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(persistedPrincipal.getKindergarten());
    }

    @Test
    @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
    @DisplayName("유치원 등록 - 실패 (교사 권한 없음)")
    void createKindergarten_Fail_Teacher() throws Exception {
        String requestBody = """
                {
                    "name": "권한테스트 유치원",
                    "address": "서울시",
                    "phone": "01099998888",
                    "openTime": "09:00",
                    "closeTime": "18:00"
                }
                """;

        mockMvc.perform(post("/api/v1/kindergartens")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("유치원 등록 - 이미 소속된 원장은 중복 등록 불가")
    void createKindergarten_Fail_AlreadyAssignedPrincipal() throws Exception {
        String requestBody = """
                {
                    "name": "중복 등록 유치원",
                    "address": "서울시",
                    "phone": "01099998888",
                    "openTime": "09:00",
                    "closeTime": "18:00"
                }
                """;

        mockMvc.perform(post("/api/v1/kindergartens")
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AP006"));
    }

    @Test
    @DisplayName("유치원 목록 조회 - 성공")
    void getKindergartens_Success() throws Exception {
        mockMvc.perform(get("/api/v1/kindergartens").with(authenticated(teacherMember)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("다른 유치원 조회·수정·삭제 - 실패 (tenant 경계)")
    void accessOtherKindergarten_Fail_TenantBoundary() throws Exception {
        Kindergarten otherKindergarten = testData.createKindergarten();
        String requestBody = """
                {
                    "name": "변경 시도",
                    "address": "서울시",
                    "phone": "01012345678",
                    "openTime": "09:00",
                    "closeTime": "18:00"
                }
                """;

        mockMvc.perform(get("/api/v1/kindergartens/{id}", otherKindergarten.getId())
                        .with(authenticated(principalMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AP007"));

        mockMvc.perform(put("/api/v1/kindergartens/{id}", otherKindergarten.getId())
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AP007"));

        mockMvc.perform(delete("/api/v1/kindergartens/{id}", otherKindergarten.getId())
                        .with(authenticated(principalMember))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AP007"));
    }
}
