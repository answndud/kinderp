package com.kinderp.global.security.user;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 사용자 정보
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Member member;

    public CustomUserDetails(Member member) {
        this.member = member;
    }

    public Member getMember() {
        return member;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ROLE_ 접두사를 포함한 권한 반환
        return Collections.singletonList(
                new SimpleGrantedAuthority(member.getRole().getKey())
        );
    }

    @Override
    public String getPassword() {
        return member.getPassword() == null ? "" : member.getPassword();
    }

    @Override
    public String getUsername() {
        return member.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 승인 대기(PENDING) 상태도 로그인은 허용하고,
        // 화면 접근은 인터셉터/권한으로 제한한다.
        return !com.kinderp.domain.member.entity.MemberStatus.INACTIVE.equals(member.getStatus());
    }

    /**
     * 회원 ID 반환
     */
    public Long getMemberId() {
        return member.getId();
    }

    /**
     * 역할 반환
     */
    public MemberRole getRole() {
        return member.getRole();
    }
}
