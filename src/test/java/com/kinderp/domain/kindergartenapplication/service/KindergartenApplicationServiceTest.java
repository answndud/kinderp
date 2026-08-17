package com.kinderp.domain.kindergartenapplication.service;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.domainaudit.service.DomainAuditLogService;
import com.kinderp.domain.kindergartenapplication.entity.KindergartenApplication;
import com.kinderp.domain.kindergartenapplication.repository.KindergartenApplicationRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("fast")
public class KindergartenApplicationServiceTest {

    @Mock
    private KindergartenApplicationRepository applicationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private KindergartenRepository kindergartenRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private DomainAuditLogService domainAuditLogService;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private KindergartenApplicationService kindergartenApplicationService;

    @Test
    @DisplayName("지원서 승인 시 같은 교사의 다른 대기 지원서만 자동 거절한다")
    void approve_RejectsOnlySameTeacherPendingApplications() {
        Kindergarten kgA = Kindergarten.create("A유치원", "addr", "02", LocalTime.of(9, 0), LocalTime.of(18, 0));
        Kindergarten kgB = Kindergarten.create("B유치원", "addr", "02", LocalTime.of(9, 0), LocalTime.of(18, 0));
        ReflectionTestUtils.setField(kgA, "id", 100L);
        ReflectionTestUtils.setField(kgB, "id", 101L);

        Member teacher = Member.create("teacher@test.com", "encoded", "교사", "010", MemberRole.TEACHER);
        Member principal = Member.create("principal@test.com", "encoded", "원장", "010", MemberRole.PRINCIPAL);
        principal.assignKindergarten(kgA);
        ReflectionTestUtils.setField(teacher, "id", 1L);

        KindergartenApplication approvedTarget = KindergartenApplication.create(teacher, kgA, "지원합니다");
        KindergartenApplication otherPending = KindergartenApplication.create(teacher, kgB, "다른 곳도 지원");
        ReflectionTestUtils.setField(approvedTarget, "id", 10L);
        ReflectionTestUtils.setField(otherPending, "id", 11L);

        when(applicationRepository.findByIdAndDeletedAtIsNullForUpdate(10L)).thenReturn(Optional.of(approvedTarget));
        when(memberRepository.findById(20L)).thenReturn(Optional.of(principal));
        when(applicationRepository.findPendingApplicationsByTeacherId(1L))
                .thenReturn(List.of(approvedTarget, otherPending));

        kindergartenApplicationService.approve(10L, 20L);

        assertThat(approvedTarget.isApproved()).isTrue();
        assertThat(otherPending.isRejected()).isTrue();
        assertThat(otherPending.getProcessedBy()).isEqualTo(principal);
        verify(applicationRepository).findPendingApplicationsByTeacherId(1L);
        verify(applicationRepository, never()).findPendingApplicationsByKindergartenId(anyLong());
    }

    @Test
    @DisplayName("원장 소속 유치원이 없으면 승인 시 접근 거부 예외를 반환한다")
    void approve_FailsWhenPrincipalHasNoKindergarten() {
        Kindergarten kgA = Kindergarten.create("A유치원", "addr", "02", LocalTime.of(9, 0), LocalTime.of(18, 0));
        Member teacher = Member.create("teacher@test.com", "encoded", "교사", "010", MemberRole.TEACHER);
        Member principalWithoutKindergarten = Member.create("principal@test.com", "encoded", "원장", "010", MemberRole.PRINCIPAL);
        KindergartenApplication application = KindergartenApplication.create(teacher, kgA, "지원합니다");

        when(applicationRepository.findByIdAndDeletedAtIsNullForUpdate(10L)).thenReturn(Optional.of(application));
        when(memberRepository.findById(20L)).thenReturn(Optional.of(principalWithoutKindergarten));

        Throwable throwable = catchThrowable(() -> kindergartenApplicationService.approve(10L, 20L));

        assertThat(throwable).isInstanceOf(BusinessException.class);
        BusinessException businessException = (BusinessException) throwable;
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.KINDERGARTEN_ACCESS_DENIED);

        verify(applicationRepository, never()).findPendingApplicationsByTeacherId(anyLong());
    }
}
