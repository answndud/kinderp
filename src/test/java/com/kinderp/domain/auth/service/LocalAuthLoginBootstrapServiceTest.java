package com.kinderp.domain.auth.service;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("fast")
public class LocalAuthLoginBootstrapServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private KindergartenRepository kindergartenRepository;

    @InjectMocks
    private LocalAuthLoginBootstrapService localAuthLoginBootstrapService;

    @Test
    @DisplayName("로컬 로그인: 유치원 미배정 원장은 기본 유치원으로 자동 배정된다")
    void afterAuthenticated_AssignsDefaultKindergarten_ForPrincipal() {
        String email = "new-principal@test.com";
        Member principal = Member.create(email, "encoded", "신규원장", "01012345678", MemberRole.PRINCIPAL);
        Kindergarten kindergarten = Kindergarten.create("해바라기 유치원", "addr", "02", LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(principal));
        when(kindergartenRepository.findByName("해바라기 유치원")).thenReturn(Optional.of(kindergarten));

        localAuthLoginBootstrapService.afterAuthenticated(email);

        assertThat(principal.getKindergarten()).isEqualTo(kindergarten);
        verify(memberRepository).findByEmail(email);
        verify(kindergartenRepository).findByName("해바라기 유치원");
        verifyNoMoreInteractions(kindergartenRepository);
    }

    @Test
    @DisplayName("로컬 로그인: 기본 유치원이 없으면 첫 번째 유치원으로 배정한다")
    void afterAuthenticated_AssignsFirstKindergarten_WhenDefaultMissing() {
        String email = "new-principal2@test.com";
        Member principal = Member.create(email, "encoded", "신규원장2", "01012345678", MemberRole.PRINCIPAL);
        Kindergarten fallback = Kindergarten.create("꿈나무 유치원", "addr", "02", LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(principal));
        when(kindergartenRepository.findByName("해바라기 유치원")).thenReturn(Optional.empty());
        when(kindergartenRepository.findAllByOrderByNameAsc()).thenReturn(List.of(fallback));

        localAuthLoginBootstrapService.afterAuthenticated(email);

        assertThat(principal.getKindergarten()).isEqualTo(fallback);
        verify(kindergartenRepository).findAllByOrderByNameAsc();
    }
}
