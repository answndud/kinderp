package com.kinderp.domain.authaudit.entity;

import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "kindergarten_id")
    private Long kindergartenId;

    @Column(name = "email", length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private MemberAuthProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AuthAuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AuthAuditResult result;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    public static AuthAuditLog create(Long memberId,
                                      Long kindergartenId,
                                      String email,
                                      MemberAuthProvider provider,
                                      AuthAuditEventType eventType,
                                      AuthAuditResult result,
                                      String reason,
                                      String clientIp) {
        AuthAuditLog authAuditLog = new AuthAuditLog();
        authAuditLog.memberId = memberId;
        authAuditLog.kindergartenId = kindergartenId;
        authAuditLog.email = email;
        authAuditLog.provider = provider;
        authAuditLog.eventType = eventType;
        authAuditLog.result = result;
        authAuditLog.reason = reason;
        authAuditLog.clientIp = clientIp;
        return authAuditLog;
    }

    public static AuthAuditLog create(Long memberId,
                                      String email,
                                      MemberAuthProvider provider,
                                      AuthAuditEventType eventType,
                                      AuthAuditResult result,
                                      String reason,
                                      String clientIp) {
        return create(memberId, null, email, provider, eventType, result, reason, clientIp);
    }
}
