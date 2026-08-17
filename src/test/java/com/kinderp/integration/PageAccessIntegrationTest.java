package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 페이지 접근 통합 테스트
 * 각 역할(원장, 교사, 학부모)별로 페이지 접근 권한을 테스트
 */
@DisplayName("페이지 접근 권한 테스트")
@Tag("integration")
class PageAccessIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("인증 없는 요청")
    class UnauthenticatedRequests {

        @Test
        @DisplayName("메인 페이지 - 접근 가능")
        void mainPage_Accessible() throws Exception {
            mockMvc.perform(get("/"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("index"));
        }

        @Test
        @DisplayName("로그인 페이지 - 접근 가능")
        void loginPage_Accessible() throws Exception {
            mockMvc.perform(get("/login"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/login"));
        }

        @Test
        @DisplayName("회원가입 페이지 - 접근 가능")
        void signUpPage_Accessible() throws Exception {
            mockMvc.perform(get("/signup"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/signup"));
        }

        @Test
        @DisplayName("유치원 생성 페이지 - 인증 필요")
        void kindergartenCreatePage_RequiresAuthentication() throws Exception {
            mockMvc.perform(get("/kindergarten/create"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("구글 OAuth2 시작 URL - 접근 가능")
        void oauth2GoogleAuthorization_Accessible() throws Exception {
            mockMvc.perform(get("/oauth2/authorization/google"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("카카오 OAuth2 시작 URL - 접근 가능")
        void oauth2KakaoAuthorization_Accessible() throws Exception {
            mockMvc.perform(get("/oauth2/authorization/kakao"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("알림장 페이지 - 리다이렉트 (로그인 필요)")
        void notepadPage_RedirectsToLogin() throws Exception {
            mockMvc.perform(get("/notepad"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("출결관리 페이지 - 리다이렉트 (로그인 필요)")
        void attendancePage_RedirectsToLogin() throws Exception {
            mockMvc.perform(get("/attendance"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("공지사항 페이지 - 리다이렉트 (로그인 필요)")
        void announcementsPage_RedirectsToLogin() throws Exception {
            mockMvc.perform(get("/announcements"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("원장(PRINCIPAL) 접근 테스트")
    class PrincipalAccessTest {

        @BeforeEach
        void setUp() {
            authenticateAsPrincipal();
        }

        @Test
        @DisplayName("메인 페이지 - 접근 가능")
        void mainPage_Accessible() throws Exception {
            mockMvc.perform(get("/"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("index"));
        }

        @Test
        @DisplayName("알림장 페이지 - 접근 가능")
        void notepadPage_Accessible() throws Exception {
            mockMvc.perform(get("/notepad"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("notepad/notepad"));
        }

        @Test
        @DisplayName("출결관리 페이지 - 접근 가능")
        void attendancePage_Accessible() throws Exception {
            mockMvc.perform(get("/attendance"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("attendance/attendance"));
        }

        @Test
        @DisplayName("공지사항 페이지 - 접근 가능")
        void announcementsPage_Accessible() throws Exception {
            mockMvc.perform(get("/announcements"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("announcement/announcements"));
        }

        @Test
        @DisplayName("유치원 생성 페이지 - 원장만 접근 가능")
        void kindergartenCreatePage_Accessible() throws Exception {
            mockMvc.perform(get("/kindergarten/create"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("kindergarten/create"));
        }
    }

    @Nested
    @DisplayName("교사(TEACHER) 접근 테스트")
    class TeacherAccessTest {

        @BeforeEach
        void setUp() {
            authenticateAsTeacher();
        }

        @Test
        @DisplayName("메인 페이지 - 접근 가능")
        void mainPage_Accessible() throws Exception {
            mockMvc.perform(get("/"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("index"));
        }

        @Test
        @DisplayName("알림장 페이지 - 접근 가능")
        void notepadPage_Accessible() throws Exception {
            mockMvc.perform(get("/notepad"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("notepad/notepad"));
        }

        @Test
        @DisplayName("출결관리 페이지 - 접근 가능")
        void attendancePage_Accessible() throws Exception {
            mockMvc.perform(get("/attendance"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("attendance/attendance"));
        }

        @Test
        @DisplayName("공지사항 페이지 - 접근 가능")
        void announcementsPage_Accessible() throws Exception {
            mockMvc.perform(get("/announcements"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("announcement/announcements"));
        }

        @Test
        @DisplayName("유치원 선택 페이지 - 교사 접근 가능")
        void kindergartenSelectPage_Accessible() throws Exception {
            mockMvc.perform(get("/kindergarten/select"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("kindergarten/select"));
        }
    }

    @Nested
    @DisplayName("학부모(PARENT) 접근 테스트")
    class ParentAccessTest {

        @BeforeEach
        void setUp() {
            authenticateAsParent();
        }

        @Test
        @DisplayName("메인 페이지 - 접근 가능")
        void mainPage_Accessible() throws Exception {
            mockMvc.perform(get("/"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("index"));
        }

        @Test
        @DisplayName("알림장 페이지 - 접근 가능 (조회만)")
        void notepadPage_Accessible() throws Exception {
            mockMvc.perform(get("/notepad"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("notepad/notepad"));
        }

        @Test
        @DisplayName("출결관리 페이지 - 접근 가능 (조회만)")
        void attendancePage_Accessible() throws Exception {
            mockMvc.perform(get("/attendance"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("attendance/attendance"));
        }

        @Test
        @DisplayName("공지사항 페이지 - 접근 가능")
        void announcementsPage_Accessible() throws Exception {
            mockMvc.perform(get("/announcements"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(view().name("announcement/announcements"));
        }

        @Test
        @DisplayName("유치원 생성 페이지 - 학부모 접근 거부")
        void kindergartenCreatePage_Forbidden() throws Exception {
            mockMvc.perform(get("/kindergarten/create"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("로그아웃 테스트")
    class LogoutTest {

        @Test
        @DisplayName("로그아웃 - 성공")
        void logout_Success() throws Exception {
            authenticateAsPrincipal();

            mockMvc.perform(post("/logout").with(csrf()))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }
}
