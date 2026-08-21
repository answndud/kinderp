package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("알림 API 테스트")
@Tag("integration")
class NotificationApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
    @DisplayName("알림 생성/전체 읽음 - 성공")
    void createAndReadAll_Success_Principal() throws Exception {
        String requestBody = """
                {
                    "receiverId": 1,
                    "type": "SYSTEM",
                    "title": "점검 안내",
                    "content": "오늘 저녁 시스템 점검이 예정되어 있습니다.",
                    "linkUrl": "/notifications"
                }
                """;

        mockMvc.perform(post("/api/v1/notifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("외부 알림 생성 - 실패 (사용자별 rate limit 초과)")
    void createNotification_Fail_RateLimitedByUser() throws Exception {
        String requestBody = """
                {
                    "receiverId": %d,
                    "type": "SYSTEM",
                    "title": "rate limit 테스트",
                    "content": "반복 발송 차단"
                }
                """.formatted(parentMember.getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/notifications")
                            .with(authenticated(principalMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/notifications")
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("C005"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Retry-After")).isEqualTo("60"));
    }

    @Test
    @DisplayName("외부 알림 생성 - 실패 (IP별 rate limit 초과)")
    void createNotification_Fail_RateLimitedByIp() throws Exception {
        String requestBody = """
                {
                    "receiverId": %d,
                    "type": "SYSTEM",
                    "title": "IP rate limit 테스트",
                    "content": "반복 발송 차단"
                }
                """.formatted(parentMember.getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/notifications")
                            .with(authenticated(principalMember))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/notifications")
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/notifications")
                        .with(authenticated(teacherMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("C005"));
    }

    @Test
    @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
    @DisplayName("알림 생성 - 실패 (학부모 권한 없음)")
    void createNotification_Fail_Parent() throws Exception {
        String requestBody = """
                {
                    "receiverId": 1,
                    "type": "SYSTEM",
                    "title": "권한 테스트",
                    "content": "학부모는 생성 권한이 없습니다."
                }
                """;

        mockMvc.perform(post("/api/v1/notifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("알림 생성 - 실패 (다른 유치원 사용자에게 발송 불가)")
    void createNotification_Fail_DifferentKindergartenReceiver() throws Exception {
        var otherKindergarten = testData.createKindergarten();
        var otherParent = createMemberInKindergarten(
                "notification-other-parent@test.com",
                "다른 유치원 학부모",
                MemberRole.PARENT,
                otherKindergarten
        );

        String requestBody = """
                {
                    "receiverId": %d,
                    "type": "SYSTEM",
                    "title": "교차 유치원 발송 시도",
                    "content": "차단되어야 합니다."
                }
                """.formatted(otherParent.getId());

        mockMvc.perform(post("/api/v1/notifications")
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AP007"));
    }

    @Test
    @DisplayName("삭제된 알림 상세 조회 - 실패 (soft delete 이후 재조회 불가)")
    void getNotification_Fail_AfterSoftDelete() throws Exception {
        String requestBody = """
                {
                    "receiverId": %d,
                    "type": "SYSTEM",
                    "title": "삭제 후 재조회 차단",
                    "content": "soft delete 검증",
                    "linkUrl": "/notifications"
                }
                """.formatted(parentMember.getId());

        MvcResult createResult = mockMvc.perform(post("/api/v1/notifications")
                        .with(authenticated(principalMember))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        long notificationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .asLong();

        mockMvc.perform(delete("/api/v1/notifications/{id}", notificationId)
                        .with(authenticated(parentMember))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/notifications/{id}", notificationId)
                        .with(authenticated(parentMember)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NT001"));
    }
}
