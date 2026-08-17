package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.announcement.entity.Announcement;
import com.kinderp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 공지사항 API 통합 테스트
 */
@DisplayName("공지사항 API 테스트")
@Tag("integration")
class AnnouncementApiIntegrationTest extends BaseIntegrationTest {

    @AfterEach
    void cleanUp() {
        testData.cleanup();
    }

    @Nested
    @DisplayName("공지사항 생성 API")
    class CreateAnnouncementTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("공지사항 생성 - 성공 (원장)")
        void createAnnouncement_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "kindergartenId": 1,
                        "title": "2025년 봄학기 원아 모집 안내",
                        "content": "2025년 봄학기 원아 모집이 시작되었습니다.",
                        "isImportant": true
                    }
                    """;

            mockMvc.perform(post("/api/v1/announcements?kindergartenId=1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("2025년 봄학기 원아 모집 안내"));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("공지사항 생성 - 성공 (교사)")
        void createAnnouncement_Success_Teacher() throws Exception {
            String requestBody = """
                    {
                        "kindergartenId": 1,
                        "title": "현장체험 안내",
                        "content": "다음 주 화요일로 현장체험 안내",
                        "isImportant": false
                    }
                    """;

            mockMvc.perform(post("/api/v1/announcements?kindergartenId=1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.writerName").value("김선생"));
        }

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("공지사항 생성 - 실패 (다른 유치원에는 작성 불가)")
        void createAnnouncement_Fail_DifferentKindergarten() throws Exception {
            var anotherKindergarten = testData.createKindergarten();

            String requestBody = """
                    {
                        "kindergartenId": %d,
                        "title": "다른 유치원 공지",
                        "content": "권한 검증",
                        "isImportant": false
                    }
                    """.formatted(anotherKindergarten.getId());

            mockMvc.perform(post("/api/v1/announcements")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("AP007"));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("공지사항 생성 - 실패 (학부모는 권한 없음)")
        void createAnnouncement_Fail_Parent() throws Exception {
            String requestBody = """
                    {
                        "kindergartenId": 1,
                        "title": "테스트 공지",
                        "content": "내용"
                    }
                    """;

            mockMvc.perform(post("/api/v1/announcements?kindergartenId=1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("공지사항 조회 API")
    class GetAnnouncementTest {

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("공지사항 단건 조회 - 성공")
        void getAnnouncement_Success() throws Exception {
            mockMvc.perform(get("/api/v1/announcements/1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("유치원별 공지사항 목록 조회 - 성공")
        void getAnnouncements_Success() throws Exception {
            mockMvc.perform(get("/api/v1/announcements")
                            .param("kindergartenId", "1")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("공지사항 목록 조회 - page/size 비정상 값은 안전한 범위로 보정")
        void getAnnouncements_NormalizesInvalidPageSize() throws Exception {
            mockMvc.perform(get("/api/v1/announcements")
                            .param("kindergartenId", "1")
                            .param("page", "-1")
                            .param("size", "0"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.number").value(0))
                    .andExpect(jsonPath("$.data.size").value(10));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("공지사항 목록 조회 - 과도한 size는 최대 크기로 제한")
        void getAnnouncements_ClampsOversizedPageSize() throws Exception {
            mockMvc.perform(get("/api/v1/announcements")
                            .param("kindergartenId", "1")
                            .param("page", "0")
                            .param("size", "1000"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.size").value(100));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("중요 공지사항 목록 조회 - 성공")
        void getImportantAnnouncements_Success() throws Exception {
            mockMvc.perform(get("/api/v1/announcements/important")
                            .param("kindergartenId", "1")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("공지사항 검색 (제목) - 성공")
        void searchAnnouncements_Success() throws Exception {
            mockMvc.perform(get("/api/v1/announcements/search")
                            .param("kindergartenId", "1")
                            .param("title", "모집")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("인기 공지사항 (조회수 순) - 성공")
        void getMostViewedAnnouncements_Success() throws Exception {
            mockMvc.perform(get("/api/v1/announcements/popular")
                            .param("kindergartenId", "1")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("공지사항 단건 조회 - 실패 (다른 유치원 학부모는 조회 불가)")
        void getAnnouncement_Fail_DifferentKindergartenParent() throws Exception {
            var otherKindergarten = testData.createKindergarten();
            var otherPrincipal = createMemberInKindergarten(
                    "announcement-other-principal@test.com",
                    "다른 유치원 원장",
                    MemberRole.PRINCIPAL,
                    otherKindergarten
            );
            Announcement otherAnnouncement = announcementRepository.save(
                    Announcement.create(otherKindergarten, otherPrincipal, "외부 공지", "다른 유치원 공지")
            );

            mockMvc.perform(get("/api/v1/announcements/{id}", otherAnnouncement.getId())
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("AP007"));
        }
    }

    @Nested
    @DisplayName("공지사항 수정/삭제 API")
    class UpdateDeleteAnnouncementTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("공지사항 수정 - 성공 (원장)")
        void updateAnnouncement_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "kindergartenId": 1,
                        "title": "수정된 제목",
                        "content": "수정된 내용",
                        "isImportant": false
                    }
                    """;

            mockMvc.perform(put("/api/v1/announcements/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("공지사항 삭제 - 성공 (원장)")
        void deleteAnnouncement_Success_Principal() throws Exception {
            mockMvc.perform(delete("/api/v1/announcements/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("공지사항 삭제 - 실패 (학부모는 권한 없음)")
        void deleteAnnouncement_Fail_Parent() throws Exception {
            mockMvc.perform(delete("/api/v1/announcements/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("중요 공지 토글 API")
    class ToggleImportantTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("중요 공지 토글 - 성공 (원장)")
        void toggleImportant_Success_Principal() throws Exception {
            mockMvc.perform(patch("/api/v1/announcements/1/important")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("중요 공지 토글 - 성공 (교사)")
        void toggleImportant_Success_Teacher() throws Exception {
            mockMvc.perform(patch("/api/v1/announcements/1/important")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("중요 공지 토글 - 실패 (학부모는 권한 없음)")
        void toggleImportant_Fail_Parent() throws Exception {
            mockMvc.perform(patch("/api/v1/announcements/1/important")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }
}
