package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditLog;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.authaudit.repository.AuthAuditLogRepository;
import com.kinderp.domain.authaudit.service.AuthAuditRetentionService;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class AuthAuditRetentionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthAuditRetentionService authAuditRetentionService;

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    @Test
    @DisplayName("retention 실행 시 오래된 active 감사 로그는 archive table로 이동한다")
    void executeRetention_Success_ArchivesOldActiveLogs() {
        AuthAuditLog oldLog = authAuditLogRepository.saveAndFlush(AuthAuditLog.create(
                teacherMember.getId(),
                kindergarten.getId(),
                teacherMember.getEmail(),
                MemberAuthProvider.LOCAL,
                AuthAuditEventType.LOGIN,
                AuthAuditResult.SUCCESS,
                null,
                "198.51.100.40"
        ));
        LocalDateTime fortyDaysAgo = LocalDateTime.now().minusDays(40);
        jdbcTemplate.update(
                "UPDATE auth_audit_log SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.valueOf(fortyDaysAgo),
                Timestamp.valueOf(fortyDaysAgo),
                oldLog.getId()
        );

        AuthAuditRetentionService.AuthAuditRetentionResult result = authAuditRetentionService.executeRetention();

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_log WHERE id = ?",
                Integer.class,
                oldLog.getId()
        );
        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_log_archive WHERE id = ?",
                Integer.class,
                oldLog.getId()
        );
        Long archivedKindergartenId = jdbcTemplate.queryForObject(
                "SELECT kindergarten_id FROM auth_audit_log_archive WHERE id = ?",
                Long.class,
                oldLog.getId()
        );

        assertThat(result.archivedCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isZero();
        assertThat(activeCount).isZero();
        assertThat(archiveCount).isEqualTo(1);
        assertThat(archivedKindergartenId).isEqualTo(kindergarten.getId());
    }

    @Test
    @DisplayName("retention 실행 시 오래된 archive 감사 로그는 purge된다")
    void executeRetention_Success_PurgesOldArchivedLogs() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(420);
        LocalDateTime updatedAt = createdAt.plusHours(1);
        LocalDateTime archivedAt = LocalDateTime.now().minusDays(370);

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_log_archive (
                    id, member_id, kindergarten_id, email, provider, event_type, result, reason, client_ip,
                    created_at, updated_at, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9001L,
                teacherMember.getId(),
                kindergarten.getId(),
                teacherMember.getEmail(),
                "LOCAL",
                "LOGIN",
                "FAILURE",
                "A001",
                "203.0.113.10",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt),
                Timestamp.valueOf(archivedAt)
        );

        AuthAuditRetentionService.AuthAuditRetentionResult result = authAuditRetentionService.executeRetention();

        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_log_archive WHERE id = ?",
                Integer.class,
                9001L
        );

        assertThat(result.archivedCount()).isZero();
        assertThat(result.purgedCount()).isEqualTo(1);
        assertThat(archiveCount).isZero();
    }
}
