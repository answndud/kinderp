package com.kinderp.domain.domainaudit.entity;

import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "domain_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kindergarten_id", nullable = false)
    private Long kindergartenId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 50)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20)
    private MemberRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100)
    private DomainAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private DomainAuditTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "summary", nullable = false, length = 255)
    private String summary;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Builder
    private DomainAuditLog(Long kindergartenId,
                           Long actorId,
                           String actorName,
                           MemberRole actorRole,
                           DomainAuditAction action,
                           DomainAuditTargetType targetType,
                           Long targetId,
                           String summary,
                           String metadataJson) {
        this.kindergartenId = kindergartenId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary;
        this.metadataJson = metadataJson;
    }

    public static DomainAuditLog create(Long kindergartenId,
                                        Long actorId,
                                        String actorName,
                                        MemberRole actorRole,
                                        DomainAuditAction action,
                                        DomainAuditTargetType targetType,
                                        Long targetId,
                                        String summary,
                                        String metadataJson) {
        return DomainAuditLog.builder()
                .kindergartenId(kindergartenId)
                .actorId(actorId)
                .actorName(actorName)
                .actorRole(actorRole)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .summary(summary)
                .metadataJson(metadataJson)
                .build();
    }
}
