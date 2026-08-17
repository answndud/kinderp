package com.kinderp.domain.authaudit.repository;

import com.kinderp.domain.authaudit.entity.AuthAuditLog;
import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {

    default List<AuthAuditLog> findAllByCreatedAtAsc() {
        return findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"));
    }

    @Query(value = """
            SELECT log
            FROM AuthAuditLog log
            WHERE log.kindergartenId = :kindergartenId
              AND (:eventType IS NULL OR log.eventType = :eventType)
              AND (:result IS NULL OR log.result = :result)
              AND (:provider IS NULL OR log.provider = :provider)
              AND (:emailKeyword IS NULL OR LOWER(log.email) LIKE LOWER(CONCAT('%', :emailKeyword, '%')))
              AND (:reasonKeyword IS NULL OR LOWER(log.reason) LIKE LOWER(CONCAT('%', :reasonKeyword, '%')))
              AND (:fromCreatedAt IS NULL OR log.createdAt >= :fromCreatedAt)
              AND (:toCreatedAtExclusive IS NULL OR log.createdAt < :toCreatedAtExclusive)
            """,
            countQuery = """
            SELECT COUNT(log)
            FROM AuthAuditLog log
            WHERE log.kindergartenId = :kindergartenId
              AND (:eventType IS NULL OR log.eventType = :eventType)
              AND (:result IS NULL OR log.result = :result)
              AND (:provider IS NULL OR log.provider = :provider)
              AND (:emailKeyword IS NULL OR LOWER(log.email) LIKE LOWER(CONCAT('%', :emailKeyword, '%')))
              AND (:reasonKeyword IS NULL OR LOWER(log.reason) LIKE LOWER(CONCAT('%', :reasonKeyword, '%')))
              AND (:fromCreatedAt IS NULL OR log.createdAt >= :fromCreatedAt)
              AND (:toCreatedAtExclusive IS NULL OR log.createdAt < :toCreatedAtExclusive)
            """)
    Page<AuthAuditLog> searchByKindergartenId(@Param("kindergartenId") Long kindergartenId,
                                              @Param("eventType") AuthAuditEventType eventType,
                                              @Param("result") AuthAuditResult result,
                                              @Param("provider") MemberAuthProvider provider,
                                              @Param("emailKeyword") String emailKeyword,
                                              @Param("reasonKeyword") String reasonKeyword,
                                              @Param("fromCreatedAt") LocalDateTime fromCreatedAt,
                                              @Param("toCreatedAtExclusive") LocalDateTime toCreatedAtExclusive,
                                              Pageable pageable);

    @Query("""
            SELECT log
            FROM AuthAuditLog log
            WHERE log.kindergartenId = :kindergartenId
              AND (:eventType IS NULL OR log.eventType = :eventType)
              AND (:result IS NULL OR log.result = :result)
              AND (:provider IS NULL OR log.provider = :provider)
              AND (:emailKeyword IS NULL OR LOWER(log.email) LIKE LOWER(CONCAT('%', :emailKeyword, '%')))
              AND (:reasonKeyword IS NULL OR LOWER(log.reason) LIKE LOWER(CONCAT('%', :reasonKeyword, '%')))
              AND (:fromCreatedAt IS NULL OR log.createdAt >= :fromCreatedAt)
              AND (:toCreatedAtExclusive IS NULL OR log.createdAt < :toCreatedAtExclusive)
            """)
    List<AuthAuditLog> searchAllByKindergartenId(@Param("kindergartenId") Long kindergartenId,
                                                 @Param("eventType") AuthAuditEventType eventType,
                                                 @Param("result") AuthAuditResult result,
                                                 @Param("provider") MemberAuthProvider provider,
                                                 @Param("emailKeyword") String emailKeyword,
                                                 @Param("reasonKeyword") String reasonKeyword,
                                                 @Param("fromCreatedAt") LocalDateTime fromCreatedAt,
                                                 @Param("toCreatedAtExclusive") LocalDateTime toCreatedAtExclusive,
                                                 org.springframework.data.domain.Pageable pageable);
}
