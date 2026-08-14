package com.erp.domain.kidapplication.service;

import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.classroom.service.ClassroomCapacityService;
import com.erp.domain.domainaudit.entity.DomainAuditAction;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.kindergarten.repository.KindergartenRepository;
import com.erp.domain.kidapplication.config.KidApplicationWorkflowProperties;
import com.erp.domain.kidapplication.dto.request.AcceptKidApplicationOfferRequest;
import com.erp.domain.kidapplication.dto.request.ApproveKidApplicationRequest;
import com.erp.domain.kidapplication.dto.request.KidApplicationRequest;
import com.erp.domain.kidapplication.dto.request.OfferKidApplicationRequest;
import com.erp.domain.kidapplication.dto.request.RejectRequest;
import com.erp.domain.kidapplication.dto.request.WaitlistKidApplicationRequest;
import com.erp.domain.kidapplication.dto.response.KidApplicationResponse;
import com.erp.domain.kidapplication.entity.ApplicationStatus;
import com.erp.domain.kidapplication.entity.KidApplication;
import com.erp.domain.kidapplication.repository.KidApplicationRepository;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.member.entity.MemberStatus;
import com.erp.domain.member.repository.MemberRepository;
import com.erp.global.common.ProductTime;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import com.erp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KidApplicationService {

    private static final List<ApplicationStatus> ACTIVE_APPLICATION_STATUSES = List.of(
            ApplicationStatus.PENDING,
            ApplicationStatus.WAITLISTED,
            ApplicationStatus.OFFERED
    );

    private static final List<ApplicationStatus> REVIEW_QUEUE_STATUSES = List.of(
            ApplicationStatus.PENDING,
            ApplicationStatus.WAITLISTED,
            ApplicationStatus.OFFERED
    );

    private final KidApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    private final KindergartenRepository kindergartenRepository;
    private final ClassroomCapacityService classroomCapacityService;
    private final KidApplicationWorkflowProperties workflowProperties;
    private final AccessPolicyService accessPolicyService;
    private final KidApplicationReviewService reviewService;
    private final KidApplicationNotificationService notificationService;
    private final KidApplicationAuditService auditService;

    @Transactional
    public Long apply(KidApplicationRequest request, Long parentId) {
        Member parent = memberRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (parent.getRole() != MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_ROLE);
        }

        Kindergarten kindergarten = kindergartenRepository.findById(request.kindergartenId())
                .orElseThrow(() -> new BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND));

        validateParentKindergartenScope(parent, kindergarten.getId());

        Classroom preferredClassroom = resolvePreferredClassroom(request.preferredClassroomId(), kindergarten.getId());

        applicationRepository.findActiveApplicationByParentAndKindergarten(parentId, request.kindergartenId(), ACTIVE_APPLICATION_STATUSES)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
                });

        if (applicationRepository.existsByParentIdAndStatusInAndDeletedAtIsNull(parentId, ACTIVE_APPLICATION_STATUSES)) {
            throw new BusinessException(ErrorCode.PENDING_APPLICATION_EXISTS);
        }

        KidApplication saved;
        var existing = applicationRepository.findByParentAndKindergarten(parentId, request.kindergartenId());
        if (existing.isPresent()) {
            KidApplication application = existing.get();
            if (application.getStatus().isCancelled() || application.getStatus().isRejected() || application.getStatus().isOfferExpired()) {
                application.reapply(kindergarten, request.kidName(), request.birthDate(), request.gender(), preferredClassroom, request.notes());
                saved = applicationRepository.save(application);
            } else {
                throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
            }
        } else {
            KidApplication application = KidApplication.create(
                    parent,
                    kindergarten,
                    request.kidName(),
                    request.birthDate(),
                    request.gender(),
                    preferredClassroom,
                    request.notes()
            );

            try {
                saved = applicationRepository.save(application);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
            }
        }

        if (parent.getStatus() != MemberStatus.PENDING && parent.getKindergarten() == null) {
            parent.markPending();
        }

        notificationService.notifyStaffAboutApplication(kindergarten, parent, request.kidName());
        return saved.getId();
    }

    @Transactional
    public void approve(Long applicationId, ApproveKidApplicationRequest request, Long processorId) {
        reviewService.approve(applicationId, request, processorId);
    }

    @Transactional
    public void placeOnWaitlist(Long applicationId, WaitlistKidApplicationRequest request, Long processorId) {
        reviewService.placeOnWaitlist(applicationId, request, processorId);
    }

    @Transactional
    public void offer(Long applicationId, OfferKidApplicationRequest request, Long processorId) {
        reviewService.offer(applicationId, request, processorId);
    }

    @Transactional
    public void acceptOffer(Long applicationId, AcceptKidApplicationOfferRequest request, Long parentId) {
        reviewService.acceptOffer(applicationId, request, parentId);
    }

    @Transactional
    public void reject(Long applicationId, RejectRequest request, Long processorId) {
        reviewService.reject(applicationId, request, processorId);
    }

    @Transactional
    public void cancel(Long applicationId, Long parentId) {
        KidApplication application = getApplicationForUpdate(applicationId);

        if (!application.getParent().getId().equals(parentId)) {
            throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
        }

        application.cancel();

        Kindergarten kindergarten = application.getKindergarten();
        if (kindergarten != null) {
            notificationService.notifyStaffAboutCancellation(kindergarten, application.getParent(), application.getKidName());
            auditService.record(
                    application.getParent(),
                    DomainAuditAction.KID_APPLICATION_CANCELLED,
                    application.getParent().getName() + " 학부모가 " + application.getKidName() + "의 입학 신청을 취소했습니다.",
                    java.util.Map.of("status", application.getStatus().name()),
                    application
            );
        }
    }

    @Transactional(readOnly = true)
    public List<KidApplicationResponse> getPendingApplications(Long kindergartenId, Long memberId) {
        Member member = getStaffReviewer(memberId, kindergartenId);
        List<KidApplication> applications = applicationRepository.findPendingApplicationsByKindergartenId(kindergartenId);
        return applications.stream()
                .map(KidApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KidApplicationResponse> getReviewQueueApplications(Long kindergartenId, Long memberId) {
        getStaffReviewer(memberId, kindergartenId);
        return applicationRepository.findReviewQueueByKindergartenId(kindergartenId, REVIEW_QUEUE_STATUSES)
                .stream()
                .map(KidApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KidApplicationResponse> getMyApplications(Long parentId) {
        return applicationRepository.findByParentIdAndDeletedAtIsNullOrderByCreatedAtDesc(parentId)
                .stream()
                .map(KidApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public KidApplicationResponse getApplication(Long applicationId, Long memberId) {
        KidApplication application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Member member = accessPolicyService.getRequester(memberId);
        accessPolicyService.validateKidApplicationReadAccess(member, application);

        return KidApplicationResponse.from(application);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.kid-application.expire-offers-fixed-delay-ms:60000}")
    public void expireOffers() {
        if (!workflowProperties.isExpireOffersEnabled()) {
            return;
        }

        LocalDateTime now = ProductTime.nowDateTime();
        List<KidApplication> expiredOffers = applicationRepository.findExpiredOffers(ApplicationStatus.OFFERED, now);
        for (KidApplication application : expiredOffers) {
            if (!application.isOffered()) {
                continue;
            }
            application.markOfferExpired();
            notificationService.notifyParentAboutOfferExpired(application.getParent(), application.getKidName());
            auditService.recordSystemOfferExpired(application, now.toString());
        }
    }

    private Member getStaffReviewer(Long memberId, Long kindergartenId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getKindergarten() == null || !member.getKindergarten().getId().equals(kindergartenId)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }

        if (member.getRole() != MemberRole.PRINCIPAL && member.getRole() != MemberRole.TEACHER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    private KidApplication getApplicationForUpdate(Long applicationId) {
        return applicationRepository.findByIdAndDeletedAtIsNullForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private Classroom resolvePreferredClassroom(Long classroomId, Long kindergartenId) {
        if (classroomId == null) {
            return null;
        }
        Classroom classroom = classroomCapacityService.lockClassroom(classroomId);
        if (!classroom.getKindergarten().getId().equals(kindergartenId)) {
            throw new BusinessException(ErrorCode.CLASSROOM_NOT_BELONG_TO_KINDERGARTEN);
        }
        return classroom;
    }

    private void validateParentKindergartenScope(Member parent, Long requestedKindergartenId) {
        if (parent.getKindergarten() == null) {
            return;
        }
        if (!parent.getKindergarten().getId().equals(requestedKindergartenId)) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED_TO_KINDERGARTEN);
        }
    }

}
