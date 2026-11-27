package com.kinderp.api;

import com.kinderp.global.common.ProductTime;
import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.kid.repository.KidRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("원생 API 테스트")
@Tag("integration")
class KidApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KindergartenRepository kindergartenRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private KidRepository kidRepository;

    @AfterEach
    void cleanUp() {
        testData.cleanup();
    }

    @Nested
    @DisplayName("원생 생성 API")
    class CreateKidTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("원생 생성 - 성공 (원장)")
        void createKid_Success_Principal() throws Exception {
            String requestBody = String.format("""
                    {
                        "classroomId": %d,
                        "name": "테스트 원생",
                        "birthDate": "2020-01-01",
                        "gender": "MALE",
                        "admissionDate": "%s"
                    }
                    """, classroom.getId(), ProductTime.today());

            mockMvc.perform(post("/api/v1/kids")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("테스트 원생"));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("원생 생성 - 성공 (교사)")
        void createKid_Success_Teacher() throws Exception {
            String requestBody = String.format("""
                    {
                        "classroomId": 1,
                        "name": "테스트 원생",
                        "birthDate": "2020-01-01",
                        "gender": "FEMALE",
                        "admissionDate": "%s"
                    }
                    """, ProductTime.today());

            mockMvc.perform(post("/api/v1/kids")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생 생성 - 실패 (학부모는 권한 없음)")
        void createKid_Fail_Parent() throws Exception {
            String requestBody = String.format("""
                    {
                        "classroomId": 1,
                        "name": "테스트 원생",
                        "birthDate": "2020-01-01",
                        "gender": "MALE",
                        "admissionDate": "%s"
                    }
                    """, ProductTime.today());

            mockMvc.perform(post("/api/v1/kids")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("원생 생성 - 실패 (정원이 가득 찬 반)")
        void createKid_Fail_WhenCapacityIsFull() throws Exception {
            classroom.update("테스트반", "5세", 1);

            String requestBody = String.format("""
                    {
                        "classroomId": %d,
                        "name": "정원초과원생",
                        "birthDate": "2020-01-01",
                        "gender": "MALE",
                        "admissionDate": "%s"
                    }
                    """, classroom.getId(), ProductTime.today());

            mockMvc.perform(post("/api/v1/kids")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("CL005"));
        }
    }

    @Nested
    @DisplayName("원생 조회 API")
    class GetKidTest {

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("원생 단건 조회 - 성공")
        void getKid_Success() throws Exception {
            mockMvc.perform(get("/api/v1/kids/1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("반별 원생 목록 조회 - 성공")
        void getKidsByClassroom_Success() throws Exception {
            mockMvc.perform(get("/api/v1/kids")
                            .param("classroomId", "1"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("반별 원생 이름 검색 - 성공")
        void searchKidsByName_Success() throws Exception {
            mockMvc.perform(get("/api/v1/kids")
                            .param("classroomId", "1")
                            .param("name", "테스트"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("원생 목록 조회 - 실패 (필터 누락)")
        void getKids_Fail_WhenFilterMissing() throws Exception {
            mockMvc.perform(get("/api/v1/kids"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
        @DisplayName("학부모의 원생 목록 조회 - 성공")
        void getMyKids_Success() throws Exception {
            mockMvc.perform(get("/api/v1/kids/my-kids"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("원생 단건 조회 - 실패 (다른 유치원 교사는 접근 불가)")
        void getKid_Fail_DifferentKindergartenTeacher() throws Exception {
            Kindergarten otherKindergarten = testData.createKindergarten();
            Member otherTeacher = createMemberInKindergarten(
                    "other-teacher@test.com",
                    "다른 유치원 교사",
                    MemberRole.TEACHER,
                    otherKindergarten
            );
            Classroom otherClassroom = testData.createClassroom(otherKindergarten);
            otherClassroom.assignTeacher(otherTeacher);
            classroomRepository.save(otherClassroom);
            Kid otherKid = testData.createKid(otherClassroom);

            mockMvc.perform(get("/api/v1/kids/{id}", otherKid.getId())
                            .with(authenticated(teacherMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("AP007"));
        }
    }

    @Nested
    @DisplayName("원생 수정 API")
    class UpdateKidTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("원생 정보 수정 - 성공 (원장)")
        void updateKid_Success_Principal() throws Exception {
            String requestBody = String.format("""
                    {
                        "classroomId": 1,
                        "name": "수정된 이름",
                        "birthDate": "2020-01-01",
                        "gender": "MALE",
                        "admissionDate": "%s"
                    }
                    """, ProductTime.today());

            mockMvc.perform(put("/api/v1/kids/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("수정된 이름"));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("원생 정보 수정 - 성공 (교사)")
        void updateKid_Success_Teacher() throws Exception {
            String requestBody = String.format("""
                    {
                        "classroomId": 1,
                        "name": "수정된 이름",
                        "birthDate": "2020-01-01",
                        "gender": "FEMALE",
                        "admissionDate": "%s"
                    }
                    """, ProductTime.today());

            mockMvc.perform(put("/api/v1/kids/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("반 배정 변경 API")
    class UpdateClassroomTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("반 배정 변경 - 성공 (원장)")
        void updateClassroom_Success_Principal() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1
                    }
                    """;

            mockMvc.perform(put("/api/v1/kids/1/classroom")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("반 배정 변경 - 실패 (교사는 권한 없음)")
        void updateClassroom_Fail_Teacher() throws Exception {
            String requestBody = """
                    {
                        "classroomId": 1
                    }
                    """;

            mockMvc.perform(put("/api/v1/kids/1/classroom")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("반 배정 변경 - 실패 (정원이 가득 찬 반)")
        void updateClassroom_Fail_WhenTargetClassroomIsFull() throws Exception {
            Classroom fullClassroom = testData.createClassroom(kindergarten);
            fullClassroom.update("만석반", "6세", 1);
            classroomRepository.saveAndFlush(fullClassroom);
            kidRepository.saveAndFlush(testData.createKid(fullClassroom));

            String requestBody = """
                    {
                        "classroomId": %d
                    }
                    """.formatted(fullClassroom.getId());

            mockMvc.perform(put("/api/v1/kids/1/classroom")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("CL005"));
        }
    }

    @Nested
    @DisplayName("학부모 연결 API")
    class AssignParentTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("학부모 연결 - 성공")
        void assignParent_Success() throws Exception {
            Member extraParent = createMemberInKindergarten(
                    "parent2@test.com",
                    "추가 학부모",
                    MemberRole.PARENT,
                    kindergarten
            );

            String requestBody = String.format("""
                    {
                        "parentId": %d,
                        "relationship": "FATHER"
                    }
                    """, extraParent.getId());

            mockMvc.perform(post("/api/v1/kids/1/parents")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("학부모 연결 - 실패 (교사는 권한 없음)")
        void assignParent_Fail_Teacher() throws Exception {
            String requestBody = """
                    {
                        "parentId": 3,
                        "relationship": "FATHER"
                    }
                    """;

            mockMvc.perform(post("/api/v1/kids/1/parents")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("학부모 연결 해제 API")
    class RemoveParentTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("학부모 연결 해제 - 성공")
        void removeParent_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/kids/1/parents/3")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("학부모 연결 해제 - 실패 (교사는 권한 없음)")
        void removeParent_Fail_Teacher() throws Exception {
            mockMvc.perform(delete("/api/v1/kids/1/parents/3")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("원생 삭제 API")
    class DeleteKidTest {

        @Test
        @WithMockUser(username = "principal@test.com", roles = {"PRINCIPAL"})
        @DisplayName("원생 삭제 - 성공 (원장)")
        void deleteKid_Success_Principal() throws Exception {
            mockMvc.perform(delete("/api/v1/kids/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "teacher@test.com", roles = {"TEACHER"})
        @DisplayName("원생 삭제 - 실패 (교사는 권한 없음)")
        void deleteKid_Fail_Teacher() throws Exception {
            mockMvc.perform(delete("/api/v1/kids/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("학부모의 같은 유치원 원생 명단 접근 제한")
    class ParentRosterAccessTest {

        @Test
        @DisplayName("반별 원생 목록 - 학부모는 자기 아이가 있는 반이라도 거부")
        void getKidsByClassroom_Fail_Parent() throws Exception {
            testData.createKid(classroom);

            mockMvc.perform(get("/api/v1/kids")
                            .param("classroomId", String.valueOf(classroom.getId()))
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("유치원별 원생 목록 - 학부모는 거부")
        void getKidsByKindergarten_Fail_Parent() throws Exception {
            mockMvc.perform(get("/api/v1/kids")
                            .param("kindergartenId", String.valueOf(kindergarten.getId()))
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("원생 이름 검색 - 학부모는 거부")
        void searchKids_Fail_Parent() throws Exception {
            mockMvc.perform(get("/api/v1/kids")
                            .param("kindergartenId", String.valueOf(kindergarten.getId()))
                            .param("name", "테스트")
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("페이지 목록 - 학부모는 거부")
        void getKidsPage_Fail_Parent() throws Exception {
            mockMvc.perform(get("/api/v1/kids/page")
                            .param("kindergartenId", String.valueOf(kindergarten.getId()))
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/kids/page")
                            .param("classroomId", String.valueOf(classroom.getId()))
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("반별·유치원별 원생 목록 - 같은 유치원 교사는 성공")
        void getKids_Success_Teacher() throws Exception {
            mockMvc.perform(get("/api/v1/kids")
                            .param("classroomId", String.valueOf(classroom.getId()))
                            .with(authenticated(teacherMember)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/api/v1/kids")
                            .param("kindergartenId", String.valueOf(kindergarten.getId()))
                            .with(authenticated(teacherMember)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("내 아이 목록 - 같은 반의 다른 집 아이는 포함되지 않음")
        void getMyKids_OnlyOwnChildren() throws Exception {
            testData.createKid(classroom);

            mockMvc.perform(get("/api/v1/kids/my-kids")
                            .with(authenticated(parentMember)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(kid.getId()));
        }
    }
}
