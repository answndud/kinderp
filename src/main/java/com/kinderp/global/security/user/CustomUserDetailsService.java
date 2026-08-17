package com.kinderp.global.security.user;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 사용자 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다: " + email));

        // PENDING 상태도 로그인 허용 (승인 전 신청 화면 접근 목적)
        if (MemberStatus.INACTIVE.equals(member.getStatus())) {
            throw new UsernameNotFoundException("회원이 비활성화되었습니다: " + email);
        }

        return new CustomUserDetails(member);
    }
}
