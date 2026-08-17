package com.kinderp.domain.kindergartenapplication.service;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.kindergartenapplication.dto.request.KindergartenApplicationRequest;
import com.kinderp.domain.kindergartenapplication.dto.request.RejectRequest;
import com.kinderp.domain.kindergartenapplication.dto.response.KindergartenApplicationResponse;
import com.kinderp.domain.kindergartenapplication.entity.ApplicationStatus;
import com.kinderp.domain.kindergartenapplication.entity.KindergartenApplication;
import com.kinderp.domain.kindergartenapplication.repository.KindergartenApplicationRepository;
import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.domainaudit.service.DomainAuditLogService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KindergartenApplicationService {

    private final KindergartenApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    private final KindergartenRepository kindergartenRepository;
    private final NotificationService notificationService;
    private final DomainAuditLogService domainAuditLogService;
    private final DashboardService dashboardService;
    private final AccessPolicyService accessPolicyService;

    /**
     * 교사가 유치원에 지원
     */
    @Transactional
    public Long apply(Long teacherId, KindergartenApplicationRequest request) {
        Member teacher = memberRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (teacher.getRole() != MemberRole.TEACHER) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_ROLE);
        }

        // 이미 유치원에 배정된 교사는 지원 불가
        if (teacher.getKindergarten() != null) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED_TO_KINDERGARTEN);
        }

        // 지원 신청 시점부터 승인 전까지는 PENDING으로 고정
        if (teacher.getStatus() != MemberStatus.PENDING) {
            teacher.markPending();
        }

        Kindergarten kindergarten = kindergartenRepository.findById(request.kindergartenId())
                .orElseThrow(() -> new BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND));

        // 이미 대기 중인 지원서가 있는지 확인 (같은 유치원)
        applicationRepository.findPendingApplicationByTeacherAndKindergarten(teacherId, request.kindergartenId())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
                });

        // 대기 중인 지원서가 있으면 지원 불가 (다른 유치원 포함)
        if (applicationRepository.existsByTeacherIdAndStatusAndDeletedAtIsNull(teacherId, ApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.PENDING_APPLICATION_EXISTS);
        }

        // 이미 취소/거절된 지원서가 있으면 재신청으로 처리 (DB 유니크 제약 고려)
        KindergartenApplication saved;
        var existing = applicationRepository.findByTeacherAndKindergarten(teacherId, request.kindergartenId());
        if (existing.isPresent()) {
            KindergartenApplication application = existing.get();
            if (application.getStatus().isCancelled() || application.getStatus().isRejected()) {
                application.reapply(request.message());
                saved = applicationRepository.save(application);
            } else {
                throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
            }
        } else {
            KindergartenApplication application = KindergartenApplication.create(teacher, kindergarten, request.message());
            try {
                saved = applicationRepository.save(application);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
            }
        }

        // 유치원 원장에게 알림 발송
        notifyPrincipalAboutApplication(kindergarten, teacher);

        return saved.getId();
    }

    /**
     * 지원서 승인
     */
    @Transactional
    public void approve(Long applicationId, Long principalId) {
        KindergartenApplication application = applicationRepository.findByIdAndDeletedAtIsNullForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 원장이 해당 유치원의 소유자인지 확인
        validatePrincipalAccess(principal, application.getKindergarten().getId());
        application.approve(principal);

        // 교사에게 유치원 배정
        Member teacher = application.getTeacher();
        teacher.assignKindergarten(application.getKindergarten());
        teacher.activateMember();

        // 교사에게 알림 발송
        notifyTeacherAboutApproval(teacher, application.getKindergarten());
        domainAuditLogService.record(
                principal,
                application.getKindergarten().getId(),
                DomainAuditAction.KINDERGARTEN_APPLICATION_APPROVED,
                DomainAuditTargetType.KINDERGARTEN_APPLICATION,
                application.getId(),
                principal.getName() + "이(가) " + teacher.getName() + " 교사의 지원을 승인했습니다.",
                java.util.Map.of("teacherId", teacher.getId())
        );
        dashboardService.evictDashboardStatisticsCache(application.getKindergarten().getId());

        // 다른 대기 중인 지원서 자동 거절
        rejectOtherPendingApplications(application.getId(), teacher.getId(), principal);
    }

    /**
     * 지원서 거절
     */
    @Transactional
    public void reject(Long applicationId, RejectRequest request, Long principalId) {
        KindergartenApplication application = applicationRepository.findByIdAndDeletedAtIsNullForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 원장이 해당 유치원의 소유자인지 확인
        validatePrincipalAccess(principal, application.getKindergarten().getId());

        application.reject(request.reason(), principal);

        // 교사에게 알림 발송
        notifyTeacherAboutRejection(application.getTeacher(), application.getKindergarten(), request.reason());
        domainAuditLogService.record(
                principal,
                application.getKindergarten().getId(),
                DomainAuditAction.KINDERGARTEN_APPLICATION_REJECTED,
                DomainAuditTargetType.KINDERGARTEN_APPLICATION,
                application.getId(),
                principal.getName() + "이(가) " + application.getTeacher().getName() + " 교사의 지원을 거절했습니다.",
                java.util.Map.of("reason", request.reason())
        );
    }

    /**
     * 지원서 취소
     */
    @Transactional
    public void cancel(Long applicationId, Long teacherId) {
        KindergartenApplication application = applicationRepository.findByIdAndDeletedAtIsNullForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        if (!application.getTeacher().getId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
        }

        application.cancel();

        // 원장에게 알림 발송
        if (application.getKindergarten() != null) {
            // 유치원의 원장 찾기
            memberRepository.findByKindergartenIdAndRole(application.getKindergarten().getId(), MemberRole.PRINCIPAL)
                    .ifPresent(principal -> notificationService.notify(
                            principal.getId(),
                            NotificationType.KINDERGARTEN_APPLICATION_CANCELLED,
                            "교사 지원 취소",
                            application.getTeacher().getName() + " 교사의 유치원 지원이 취소되었습니다."
                    ));
        }
    }

    /**
     * 유치원별 대기 중인 지원서 목록
     */
    @Transactional(readOnly = true)
    public List<KindergartenApplicationResponse> getPendingApplications(Long kindergartenId, Long principalId) {
        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 권한 확인
        validatePrincipalAccess(principal, kindergartenId);

        List<KindergartenApplication> applications = applicationRepository.findPendingApplicationsByKindergartenId(kindergartenId);
        return applications.stream()
                .map(KindergartenApplicationResponse::from)
                .toList();
    }

    /**
     * 교사의 지원서 목록
     */
    @Transactional(readOnly = true)
    public List<KindergartenApplicationResponse> getMyApplications(Long teacherId) {
        List<KindergartenApplication> applications = applicationRepository.findByTeacherIdAndDeletedAtIsNullOrderByCreatedAtDesc(teacherId);
        return applications.stream()
                .map(KindergartenApplicationResponse::from)
                .toList();
    }

    /**
     * 지원서 상세 조회
     */
    @Transactional(readOnly = true)
    public KindergartenApplicationResponse getApplication(Long applicationId, Long memberId) {
        KindergartenApplication application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Member member = accessPolicyService.getRequester(memberId);
        accessPolicyService.validateKindergartenApplicationReadAccess(member, application);

        return KindergartenApplicationResponse.from(application);
    }

    /**
     * 다른 대기 중인 지원서 자동 거절
     */
    private void rejectOtherPendingApplications(Long approvedApplicationId, Long teacherId, Member principal) {
        List<KindergartenApplication> otherApplications = applicationRepository
                .findPendingApplicationsByTeacherId(teacherId)
                .stream()
                .filter(app -> !app.getId().equals(approvedApplicationId))
                .toList();

        for (KindergartenApplication app : otherApplications) {
            app.reject("다른 교사가 선정되었습니다.", principal);
            notifyTeacherAboutRejection(app.getTeacher(), app.getKindergarten(), "다른 교사가 선정되었습니다.");
        }
    }

    private void notifyPrincipalAboutApplication(Kindergarten kindergarten, Member teacher) {
        memberRepository.findByKindergartenIdAndRole(kindergarten.getId(), MemberRole.PRINCIPAL)
                .ifPresent(principal -> notificationService.notifyWithLink(
                        principal.getId(),
                        NotificationType.KINDERGARTEN_APPLICATION_SUBMITTED,
                        "새로운 교사 지원",
                        teacher.getName() + " 교사가 " + kindergarten.getName() + "에 지원했습니다.",
                        "/applications/pending"
                ));
    }

    private void validatePrincipalAccess(Member principal, Long kindergartenId) {
        if (principal.getRole() != MemberRole.PRINCIPAL
                || principal.getKindergarten() == null
                || !principal.getKindergarten().getId().equals(kindergartenId)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }
    }

    private void notifyTeacherAboutApproval(Member teacher, Kindergarten kindergarten) {
        notificationService.notifyWithLink(
                teacher.getId(),
                NotificationType.KINDERGARTEN_APPLICATION_APPROVED,
                "유치원 지원 승인",
                kindergarten.getName() + "에 지원이 승인되었습니다.",
                "/"
        );
    }

    private void notifyTeacherAboutRejection(Member teacher, Kindergarten kindergarten, String reason) {
        String content = kindergarten.getName() + "에 지원이 거절되었습니다.";
        if (reason != null && !reason.isEmpty()) {
            content += "\n사유: " + reason;
        }
        notificationService.notifyWithLink(
                teacher.getId(),
                NotificationType.KINDERGARTEN_APPLICATION_REJECTED,
                "유치원 지원 거절",
                content,
                "/applications/pending"
        );
    }
}
