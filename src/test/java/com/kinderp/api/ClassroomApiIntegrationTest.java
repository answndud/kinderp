package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("반 API 테스트")
@Tag("integration")
class ClassroomApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
    @DisplayName("반 목록 조회 - 성공 (kindergartenId 필터)")
    void getClassrooms_Success() throws Exception {
        mockMvc.perform(get("/api/v1/classrooms")
                        .param("kindergartenId", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
    @DisplayName("반 목록 조회 - 실패 (kindergartenId 누락)")
    void getClassrooms_Fail_WhenKindergartenIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/classrooms"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("다른 유치원 반 목록 조회 - 실패 (tenant 경계)")
    void getClassrooms_Fail_OtherKindergarten() throws Exception {
        var otherKindergarten = testData.createKindergarten();

        mockMvc.perform(get("/api/v1/classrooms")
                        .param("kindergartenId", otherKindergarten.getId().toString())
                        .with(authenticated(teacherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP007"));
    }

    @Test
    @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
    @DisplayName("반 생성 - 실패 (학부모 권한 없음)")
    void createClassroom_Fail_Parent() throws Exception {
        String requestBody = """
                {
                    "kindergartenId": 1,
                    "name": "새반",
                    "ageGroup": "6세"
                }
                """;

        mockMvc.perform(post("/api/v1/classrooms")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
    @DisplayName("반 생성 - 성공 (정원 포함)")
    void createClassroom_Success_WithCapacity() throws Exception {
        String requestBody = """
                {
                    "kindergartenId": 1,
                    "name": "대기열반",
                    "ageGroup": "6세",
                    "capacity": 12
                }
                """;

        mockMvc.perform(post("/api/v1/classrooms")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.capacity").value(12));
    }
}
