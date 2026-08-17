package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditLog;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.authaudit.repository.AuthAuditLogRepository;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class AuthAuditApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthAuditLogService authAuditLogService;

    @Test
    @DisplayName("원장은 자기 유치원 범위로 귀속된 감사 로그만 조회할 수 있다")
    void getAuditLogs_Success_PrincipalOnlySeesOwnKindergartenLogs() throws Exception {
        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                teacherMember.getId(),
                kindergarten.getId(),
                teacherMember.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.SUCCESS,
                null,
                "198.51.100.10"
        ));

        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                null,
                kindergarten.getId(),
                teacherMember.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.FAILURE,
                "A001",
                "198.51.100.11"
        ));

        Kindergarten otherKindergarten = kindergartenRepository.save(
                Kindergarten.create("다른 유치원", "부산시", "010-2222-3333", LocalTime.of(9, 0), LocalTime.of(18, 0))
        );
        Member otherPrincipal = testData.createTestMember("other-principal@test.com", "다른원장", MemberRole.PRINCIPAL, "test1234");
        otherPrincipal.assignKindergarten(otherKindergarten);
        memberRepository.saveAndFlush(otherPrincipal);

        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                otherPrincipal.getId(),
                otherKindergarten.getId(),
                otherPrincipal.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.FAILURE,
                "A001",
                "203.0.113.22"
        ));

        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                null,
                "unknown@test.com",
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.FAILURE,
                "A001",
                "203.0.113.77"
        ));

        mockMvc.perform(get("/api/v1/auth/audit-logs")
                        .with(authenticated(principalMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].reason").value("A001"))
                .andExpect(jsonPath("$.data.content[0].email").value("teacher@test.com"))
                .andExpect(jsonPath("$.data.content[0].eventType").value("LOGIN"))
                .andExpect(jsonPath("$.data.content[1].email").value("teacher@test.com"))
                .andExpect(jsonPath("$.data.content[1].result").value("SUCCESS"));
    }

    @Test
    @DisplayName("known email 로그인 실패는 memberId가 없어도 kindergartenId를 채워 저장한다")
    void recordLoginFailure_Success_KnownEmailStoresKindergartenId() {
        Long committedKindergartenId = readCommitted(() -> {
            Kindergarten committedKindergarten = kindergartenRepository.save(
                    Kindergarten.create("커밋 유치원", "대전시", "010-5555-5555", LocalTime.of(9, 0), LocalTime.of(18, 0))
            );
            Member committedTeacher = testData.createTestMember(
                    "committed-teacher@test.com",
                    "커밋교사",
                    MemberRole.TEACHER,
                    "test1234"
            );
            committedTeacher.assignKindergarten(committedKindergarten);
            memberRepository.saveAndFlush(committedTeacher);
            return committedKindergarten.getId();
        });

        authAuditLogService.recordLoginFailure("committed-teacher@test.com", MemberAuthProvider.LOCAL, "203.0.113.88", "A001");

        var logs = readCommitted(authAuditLogRepository::findAllByCreatedAtAsc);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getMemberId()).isNull();
        assertThat(logs.get(0).getKindergartenId()).isEqualTo(committedKindergartenId);
        assertThat(logs.get(0).getEmail()).isEqualTo("committed-teacher@test.com");
        assertThat(logs.get(0).getReason()).isEqualTo("A001");
    }

    @Test
    @DisplayName("원장은 eventType, result, provider, email, date 필터로 감사 로그를 조회할 수 있다")
    void getAuditLogs_Success_WithFilters() throws Exception {
        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                teacherMember.getId(),
                kindergarten.getId(),
                teacherMember.getEmail(),
                MemberAuthProvider.GOOGLE,
                AuthAuditEventType.SOCIAL_UNLINK,
                AuthAuditResult.FAILURE,
                "A010",
                "198.51.100.12"
        ));
        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                parentMember.getId(),
                kindergarten.getId(),
                parentMember.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.SUCCESS,
                null,
                "198.51.100.14"
        ));

        mockMvc.perform(get("/api/v1/auth/audit-logs")
                        .with(authenticated(principalMember))
                        .param("eventType", "SOCIAL_UNLINK")
                        .param("result", "FAILURE")
                        .param("provider", "GOOGLE")
                        .param("email", "teacher")
                        .param("reason", "A010")
                        .param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reason").value("A010"))
                .andExpect(jsonPath("$.data.content[0].provider").value("GOOGLE"));
    }

    @Test
    @DisplayName("교사는 인증 감사 로그 조회가 차단된다")
    void getAuditLogs_Fail_TeacherForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/auth/audit-logs")
                        .with(authenticated(teacherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("원장은 비정상 page/size로도 인증 감사 로그를 안전하게 조회할 수 있다")
    void getAuditLogs_NormalizesInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/auth/audit-logs")
                        .with(authenticated(principalMember))
                        .param("page", "-1")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("원장은 과도한 size 요청 시 최대 크기로 제한된 인증 감사 로그를 조회한다")
    void getAuditLogs_ClampsOversizedPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/auth/audit-logs")
                        .with(authenticated(principalMember))
                        .param("page", "0")
                        .param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("원장은 같은 필터 조건으로 감사 로그 CSV를 export할 수 있다")
    void exportAuditLogs_Success_PrincipalCanDownloadCsv() throws Exception {
        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                teacherMember.getId(),
                kindergarten.getId(),
                teacherMember.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.FAILURE,
                "A001",
                "198.51.100.30"
        ));
        authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                parentMember.getId(),
                kindergarten.getId(),
                parentMember.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.SUCCESS,
                null,
                "198.51.100.31"
        ));

        mockMvc.perform(get("/api/v1/auth/audit-logs/export")
                        .with(authenticated(principalMember))
                        .param("eventType", "LOGIN")
                        .param("result", "FAILURE")
                        .param("email", "teacher")
                        .param("reason", "A001")
                        .param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("createdAt,eventType,result,email,provider,reason,clientIp,memberId")))
                .andExpect(content().string(containsString("\"teacher@test.com\"")))
                .andExpect(content().string(containsString("\"A001\"")));
    }

    @Test
    @DisplayName("교사는 인증 감사 로그 CSV export가 차단된다")
    void exportAuditLogs_Fail_TeacherForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/auth/audit-logs/export")
                        .with(authenticated(teacherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A004"));
    }
}
