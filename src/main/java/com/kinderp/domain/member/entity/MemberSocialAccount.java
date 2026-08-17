package com.kinderp.domain.member.entity;

import com.kinderp.global.common.BaseEntity;
import com.kinderp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "member_social_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_social_account_provider_provider_id", columnNames = {"provider", "provider_id"}),
                @UniqueConstraint(name = "uk_member_social_account_member_provider", columnNames = {"member_id", "provider"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private MemberAuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "unlinked_at")
    private LocalDateTime unlinkedAt;

    public static MemberSocialAccount create(Member member, MemberAuthProvider provider, String providerId) {
        MemberSocialAccount socialAccount = new MemberSocialAccount();
        socialAccount.member = member;
        socialAccount.provider = provider;
        socialAccount.providerId = providerId;
        socialAccount.unlinkedAt = null;
        return socialAccount;
    }

    public boolean isSameProvider(MemberAuthProvider provider) {
        return this.provider == provider;
    }

    public boolean matches(MemberAuthProvider provider, String providerId) {
        return this.provider == provider && this.providerId.equals(providerId);
    }

    public boolean hasProviderId(String providerId) {
        return this.providerId.equals(providerId);
    }

    public boolean isActive() {
        return this.unlinkedAt == null;
    }

    public void unlink() {
        this.unlinkedAt = ProductTime.nowDateTime();
    }

    public void relink() {
        this.unlinkedAt = null;
    }
}
