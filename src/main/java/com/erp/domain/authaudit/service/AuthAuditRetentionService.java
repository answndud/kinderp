package com.erp.domain.authaudit.service;

import com.erp.domain.authaudit.config.AuthAuditRetentionProperties;
import com.erp.global.common.ProductTime;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAuditRetentionService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthAuditRetentionProperties retentionProperties;

    @Scheduled(cron = "${app.security.auth-audit-retention.cron:0 30 3 * * *}")
    public void runScheduledRetention() {
        executeRetention();
    }

    @Transactional
    public AuthAuditRetentionResult executeRetention() {
        if (!retentionProperties.isEnabled()) {
            return new AuthAuditRetentionResult(0, 0);
        }

        LocalDateTime now = ProductTime.nowDateTime();
        int archived = archiveEligibleLogs(now.minus(normalizeDuration(
                retentionProperties.getArchiveAfter(),
                Duration.ofDays(30)
        )), now);
        int purged = purgeArchivedLogs(now.minus(normalizeDuration(
                retentionProperties.getDeleteAfter(),
                Duration.ofDays(365)
        )));

        if (archived > 0 || purged > 0) {
            log.info("Auth audit retention completed. archived={}, purged={}", archived, purged);
        }

        return new AuthAuditRetentionResult(archived, purged);
    }

    private int archiveEligibleLogs(LocalDateTime archiveBefore, LocalDateTime archivedAt) {
        int totalArchived = 0;
        int batchSize = resolveBatchSize();

        while (true) {
            List<Long> ids = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM auth_audit_log
                    WHERE created_at < ?
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    """,
                    Long.class,
                    Timestamp.valueOf(archiveBefore),
                    batchSize
            );

            if (ids.isEmpty()) {
                return totalArchived;
            }

            totalArchived += insertArchiveRows(ids, archivedAt);
            deleteRows("auth_audit_log", ids);

            if (ids.size() < batchSize) {
                return totalArchived;
            }
        }
    }

    private int purgeArchivedLogs(LocalDateTime deleteBefore) {
        int totalPurged = 0;
        int batchSize = resolveBatchSize();

        while (true) {
            List<Long> ids = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM auth_audit_log_archive
                    WHERE archived_at < ?
                    ORDER BY archived_at ASC, id ASC
                    LIMIT ?
                    """,
                    Long.class,
                    Timestamp.valueOf(deleteBefore),
                    batchSize
            );

            if (ids.isEmpty()) {
                return totalPurged;
            }

            totalPurged += deleteRows("auth_audit_log_archive", ids);

            if (ids.size() < batchSize) {
                return totalPurged;
            }
        }
    }

    private int insertArchiveRows(List<Long> ids, LocalDateTime archivedAt) {
        String placeholders = buildPlaceholders(ids.size());
        String sql = """
                INSERT INTO auth_audit_log_archive (
                    id, member_id, kindergarten_id, email, provider, event_type, result, reason, client_ip,
                    created_at, updated_at, archived_at
                )
                SELECT
                    id, member_id, kindergarten_id, email, provider, event_type, result, reason, client_ip,
                    created_at, updated_at, ?
                FROM auth_audit_log
                WHERE id IN (%s)
                """.formatted(placeholders);

        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setTimestamp(1, Timestamp.valueOf(archivedAt));
            bindIds(ps, ids, 2);
            return ps;
        });
    }

    private int deleteRows(String tableName, List<Long> ids) {
        String sql = "DELETE FROM %s WHERE id IN (%s)".formatted(tableName, buildPlaceholders(ids.size()));
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            bindIds(ps, ids, 1);
            return ps;
        });
    }

    private void bindIds(PreparedStatement ps, List<Long> ids, int offset) throws java.sql.SQLException {
        for (int i = 0; i < ids.size(); i++) {
            ps.setLong(offset + i, ids.get(i));
        }
    }

    private String buildPlaceholders(int count) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    private int resolveBatchSize() {
        return Math.max(retentionProperties.getBatchSize(), 1);
    }

    private Duration normalizeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    public record AuthAuditRetentionResult(int archivedCount, int purgedCount) {
    }
}
