package com.kinderp.domain.domainaudit.repository;

import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditLog;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DomainAuditLogRepository extends JpaRepository<DomainAuditLog, Long> {

    @Query(value = """
            SELECT log
            FROM DomainAuditLog log
            WHERE log.kindergartenId = :kindergartenId
              AND (:action IS NULL OR log.action = :action)
              AND (:targetType IS NULL OR log.targetType = :targetType)
              AND (:actorNameKeyword IS NULL OR LOWER(log.actorName) LIKE LOWER(CONCAT('%', :actorNameKeyword, '%')))
              AND (:summaryKeyword IS NULL OR LOWER(log.summary) LIKE LOWER(CONCAT('%', :summaryKeyword, '%')))
              AND (:fromCreatedAt IS NULL OR log.createdAt >= :fromCreatedAt)
              AND (:toCreatedAtExclusive IS NULL OR log.createdAt < :toCreatedAtExclusive)
            """,
            countQuery = """
            SELECT COUNT(log)
            FROM DomainAuditLog log
            WHERE log.kindergartenId = :kindergartenId
              AND (:action IS NULL OR log.action = :action)
              AND (:targetType IS NULL OR log.targetType = :targetType)
              AND (:actorNameKeyword IS NULL OR LOWER(log.actorName) LIKE LOWER(CONCAT('%', :actorNameKeyword, '%')))
              AND (:summaryKeyword IS NULL OR LOWER(log.summary) LIKE LOWER(CONCAT('%', :summaryKeyword, '%')))
              AND (:fromCreatedAt IS NULL OR log.createdAt >= :fromCreatedAt)
              AND (:toCreatedAtExclusive IS NULL OR log.createdAt < :toCreatedAtExclusive)
            """)
    Page<DomainAuditLog> searchByKindergartenId(@Param("kindergartenId") Long kindergartenId,
                                                @Param("action") DomainAuditAction action,
                                                @Param("targetType") DomainAuditTargetType targetType,
                                                @Param("actorNameKeyword") String actorNameKeyword,
                                                @Param("summaryKeyword") String summaryKeyword,
                                                @Param("fromCreatedAt") LocalDateTime fromCreatedAt,
                                                @Param("toCreatedAtExclusive") LocalDateTime toCreatedAtExclusive,
                                                Pageable pageable);

    @Query("""
            SELECT log
            FROM DomainAuditLog log
            WHERE log.kindergartenId = :kindergartenId
              AND (:action IS NULL OR log.action = :action)
              AND (:targetType IS NULL OR log.targetType = :targetType)
              AND (:actorNameKeyword IS NULL OR LOWER(log.actorName) LIKE LOWER(CONCAT('%', :actorNameKeyword, '%')))
              AND (:summaryKeyword IS NULL OR LOWER(log.summary) LIKE LOWER(CONCAT('%', :summaryKeyword, '%')))
              AND (:fromCreatedAt IS NULL OR log.createdAt >= :fromCreatedAt)
              AND (:toCreatedAtExclusive IS NULL OR log.createdAt < :toCreatedAtExclusive)
            """)
    List<DomainAuditLog> searchAllByKindergartenId(@Param("kindergartenId") Long kindergartenId,
                                                   @Param("action") DomainAuditAction action,
                                                   @Param("targetType") DomainAuditTargetType targetType,
                                                   @Param("actorNameKeyword") String actorNameKeyword,
                                                   @Param("summaryKeyword") String summaryKeyword,
                                                   @Param("fromCreatedAt") LocalDateTime fromCreatedAt,
                                                   @Param("toCreatedAtExclusive") LocalDateTime toCreatedAtExclusive,
                                                   org.springframework.data.domain.Pageable pageable);
}
