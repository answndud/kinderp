package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.notepad.entity.Notepad;
import com.kinderp.domain.notepad.repository.NotepadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 알림장 API 통합 테스트
 */
@DisplayName("알림장 API 테스트")
@Tag("integration")
class NotepadApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private NotepadRepository notepadRepository;

    @AfterEach
    void cleanUp() {
        testData.cleanup();
    }

    @Nested
    @DisplayName("알림장 생성 API")
    class CreateNotepadTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("알림장 생성 - 성공 (원장)")
        void createNotepad_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1,
                        "title": "오늘의 활동",
                        "content": "오늘 아이들이 미술 활동을 했습니다."
                    }
                    """;

            mockMvc.perform(post("/api/v1/notepads")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("오늘의 활동"));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("알림장 생성 - 성공 (교사)")
        void createNotepad_Success_Teacher() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1,
                        "title": "오늘의 활동",
                        "content": "오늘 아이들이 미술 활동을 했습니다."
                    }
                    """;

            mockMvc.perform(post("/api/v1/notepads")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("알림장 생성 - 실패 (학부모는 권한 없음)")
        void createNotepad_Fail_Parent() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1,
                        "title": "오늘의 활동",
                        "content": "오늘 아이들이 미술 활동을 했습니다."
                    }
                    """;

            mockMvc.perform(post("/api/v1/notepads")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("알림장 생성 - 실패 (인증 없음)")
        void createNotepad_Fail_Unauthenticated() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1,
                        "title": "오늘의 활동",
                        "content": "오늘 아이들이 미술 활동을 했습니다."
                    }
                    """;

            mockMvc.perform(post("/api/v1/notepads")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("알림장 조회 API")
    class GetNotepadTest {

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("알림장 단건 조회 - 성공")
        void getNotepad_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notepads/1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("반별 알림장 목록 조회 - 성공")
        void getClassroomNotepads_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notepads/classroom/1")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("반별 알림장 목록 조회 - page/size 비정상 값은 안전한 범위로 보정")
        void getClassroomNotepads_NormalizesInvalidPageSize() throws Exception {
            mockMvc.perform(get("/api/v1/notepads/classroom/1")
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
        @DisplayName("반별 알림장 목록 조회 - 과도한 size는 최대 크기로 제한")
        void getClassroomNotepads_ClampsOversizedPageSize() throws Exception {
            mockMvc.perform(get("/api/v1/notepads/classroom/1")
                            .param("page", "0")
                            .param("size", "1000"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.size").value(100));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생별 알림장 목록 조회 - 성공")
        void getKidNotepads_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notepads/kid/1")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("알림장 단건 조회 - 실패 (다른 유치원 학부모는 열람 불가)")
        void getNotepad_Fail_DifferentKindergartenParent() throws Exception {
            var otherKindergarten = testData.createKindergarten();
            var otherTeacher = createMemberInKindergarten(
                    "notepad-other-teacher@test.com",
                    "다른 유치원 교사",
                    MemberRole.TEACHER,
                    otherKindergarten
            );
            var otherClassroom = testData.createClassroom(otherKindergarten);
            otherClassroom.assignTeacher(otherTeacher);
            classroomRepository.save(otherClassroom);
            Notepad otherNotepad = notepadRepository.save(
                    Notepad.createClassroomNotepad(otherClassroom, otherTeacher, "외부 알림장", "다른 유치원 전용")
            );

            mockMvc.perform(get("/api/v1/notepads/{id}", otherNotepad.getId())
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("N002"));
        }
    }

    @Nested
    @DisplayName("알림장 수정/삭제 API")
    class UpdateDeleteNotepadTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("알림장 수정 - 성공 (원장)")
        void updateNotepad_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1,
                        "title": "수정된 제목",
                        "content": "수정된 내용"
                    }
                    """;

            mockMvc.perform(put("/api/v1/notepads/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("알림장 삭제 - 성공 (원장)")
        void deleteNotepad_Success_Principal() throws Exception {
            mockMvc.perform(delete("/api/v1/notepads/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("알림장 삭제 - 실패 (학부모는 권한 없음)")
        void deleteNotepad_Fail_Parent() throws Exception {
            mockMvc.perform(delete("/api/v1/notepads/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("알림장 삭제 - 실패 (다른 유치원 교사는 삭제 불가)")
        void deleteNotepad_Fail_DifferentKindergartenTeacher() throws Exception {
            var otherKindergarten = testData.createKindergarten();
            var otherTeacher = createMemberInKindergarten(
                    "notepad-delete-other-teacher@test.com",
                    "다른 유치원 교사",
                    MemberRole.TEACHER,
                    otherKindergarten
            );
            var otherClassroom = testData.createClassroom(otherKindergarten);
            otherClassroom.assignTeacher(otherTeacher);
            classroomRepository.save(otherClassroom);
            Notepad otherNotepad = notepadRepository.save(
                    Notepad.createClassroomNotepad(otherClassroom, otherTeacher, "삭제 방지", "타 유치원 알림장")
            );

            mockMvc.perform(delete("/api/v1/notepads/{id}", otherNotepad.getId())
                            .with(authenticated(teacherMember))
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("N002"));
        }
    }

    @Nested
    @DisplayName("알림장 읽음 처리 API")
    class MarkAsReadTest {

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("알림장 읽음 처리 - 성공")
        void markAsRead_Success() throws Exception {
            mockMvc.perform(post("/api/v1/notepads/1/read")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("알림장 목록 조회 - 읽음 수 반영")
        void getClassroomNotepads_ReadCountReflected() throws Exception {
            mockMvc.perform(post("/api/v1/notepads/1/read")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/api/v1/notepads/classroom/1")
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].readCount").value(1));
        }
    }
}
