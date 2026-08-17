package com.kinderp.domain.member.entity;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.global.common.BaseEntity;
import com.kinderp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 회원 엔티티
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 이메일 (로그인 ID)
     */
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    /**
     * 비밀번호 (BCrypt 암호화)
     */
    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private MemberAuthProvider authProvider = MemberAuthProvider.LOCAL;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<MemberSocialAccount> socialAccounts = new ArrayList<>();

    /**
     * 이름
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 전화번호
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 역할 (PRINCIPAL, TEACHER, PARENT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    /**
     * 상태 (ACTIVE, INACTIVE, PENDING)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status = MemberStatus.PENDING;

    /**
     * 소속 유치원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kindergarten_id")
    private Kindergarten kindergarten;

    /**
     * 탈퇴일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 회원 생성
     */
    public static Member create(String email, String encodedPassword,
                                String name, String phone, MemberRole role) {
        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.name = name;
        member.phone = phone;
        member.role = role;
        member.authProvider = MemberAuthProvider.LOCAL;
        member.providerId = null;
        member.status = MemberStatus.ACTIVE; // 기본 활성
        return member;
    }

    /**
     * 소셜 로그인 회원 생성 (비밀번호 없음)
     */
    public static Member createSocial(String email, String name,
                                      MemberRole role, MemberAuthProvider provider,
                                      String providerId) {
        Member member = new Member();
        member.email = email;
        member.password = null;
        member.name = name;
        member.role = role;
        member.authProvider = MemberAuthProvider.LOCAL;
        member.providerId = null;
        member.status = MemberStatus.ACTIVE;
        member.linkSocialAccount(provider, providerId);
        return member;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 프로필 수정
     */
    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    /**
     * 비밀번호 변경
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public boolean hasLocalPassword() {
        return this.password != null && !this.password.isBlank();
    }

    public boolean hasLinkedSocialAccount() {
        return activeSocialAccounts().findAny().isPresent();
    }

    public boolean isLinkedTo(MemberAuthProvider provider) {
        return provider != null && findActiveSocialAccount(provider).isPresent();
    }

    public boolean isLinkedTo(MemberAuthProvider provider, String providerId) {
        return provider != null
                && providerId != null
                && findActiveSocialAccount(provider)
                .filter(account -> account.hasProviderId(providerId))
                .isPresent();
    }

    public boolean hasSocialAccountHistory(MemberAuthProvider provider) {
        return provider != null && findSocialAccount(provider).isPresent();
    }

    public boolean hasProviderBindingWithDifferentIdentity(MemberAuthProvider provider, String providerId) {
        return provider != null
                && providerId != null
                && findSocialAccount(provider)
                .filter(account -> !account.hasProviderId(providerId))
                .isPresent();
    }

    public void linkSocialAccount(MemberAuthProvider provider, String providerId) {
        Optional<MemberSocialAccount> existingSocialAccount = findSocialAccount(provider);
        if (existingSocialAccount.isPresent()) {
            existingSocialAccount.get().relink();
            syncLegacyPrimarySocialAccount();
            return;
        }

        this.socialAccounts.add(MemberSocialAccount.create(this, provider, providerId));
        syncLegacyPrimarySocialAccount();
    }

    public void unlinkSocialAccount(MemberAuthProvider provider) {
        findActiveSocialAccount(provider).ifPresent(MemberSocialAccount::unlink);
        syncLegacyPrimarySocialAccount();
    }

    public boolean canUnlinkSocialAccount(MemberAuthProvider provider) {
        if (!isLinkedTo(provider)) {
            return false;
        }

        if (hasLocalPassword()) {
            return true;
        }

        return activeSocialAccounts().anyMatch(account -> !account.isSameProvider(provider));
    }

    public String getLinkedSocialProviderSummary() {
        List<String> activeProviderLabels = activeSocialAccounts()
                .map(MemberSocialAccount::getProvider)
                .map(provider -> switch (provider) {
                    case GOOGLE -> "Google";
                    case KAKAO -> "Kakao";
                    default -> provider.name();
                })
                .distinct()
                .toList();

        if (activeProviderLabels.isEmpty()) {
            return null;
        }

        return String.join(", ", activeProviderLabels);
    }

    /**
     * 유치원 배정
     */
    public void assignKindergarten(Kindergarten kindergarten) {
        this.kindergarten = kindergarten;
    }

    /**
     * 회원 활성화
     */
    public void activate() {
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * 회원 활성화 (별칭 메서드)
     */
    public void activateMember() {
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * 승인 대기 상태로 전환
     */
    public void markPending() {
        this.status = MemberStatus.PENDING;
    }

    /**
     * 회원 비활성화
     */
    public void deactivate() {
        this.status = MemberStatus.INACTIVE;
    }

    /**
     * 탈퇴 (Soft Delete)
     */
    public void withdraw() {
        this.status = MemberStatus.INACTIVE;
        this.deletedAt = ProductTime.nowDateTime();
    }

    /**
     * 비밀번호 검증
     */
    public boolean matchesPassword(String rawPassword, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, this.password);
    }

    private Optional<MemberSocialAccount> findSocialAccount(MemberAuthProvider provider) {
        return this.socialAccounts.stream()
                .filter(account -> account.isSameProvider(provider))
                .findFirst();
    }

    private Optional<MemberSocialAccount> findActiveSocialAccount(MemberAuthProvider provider) {
        return activeSocialAccounts()
                .filter(account -> account.isSameProvider(provider))
                .findFirst();
    }

    private Stream<MemberSocialAccount> activeSocialAccounts() {
        return this.socialAccounts.stream().filter(MemberSocialAccount::isActive);
    }

    private void syncLegacyPrimarySocialAccount() {
        MemberSocialAccount primarySocialAccount = activeSocialAccounts().findFirst().orElse(null);
        if (primarySocialAccount == null) {
            this.authProvider = MemberAuthProvider.LOCAL;
            this.providerId = null;
            return;
        }

        this.authProvider = primarySocialAccount.getProvider();
        this.providerId = primarySocialAccount.getProviderId();
    }
}
