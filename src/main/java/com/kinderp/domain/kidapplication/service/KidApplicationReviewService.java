package com.kinderp.domain.kidapplication.service;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.service.ClassroomCapacityService;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kidapplication.config.KidApplicationWorkflowProperties;
import com.kinderp.domain.kidapplication.dto.request.AcceptKidApplicationOfferRequest;
import com.kinderp.domain.kidapplication.dto.request.ApproveKidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.OfferKidApplicationRequest;
import com.kinderp.domain.kidapplication.dto.request.RejectRequest;
import com.kinderp.domain.kidapplication.dto.request.WaitlistKidApplicationRequest;
import com.kinderp.domain.kidapplication.entity.KidApplication;
import com.kinderp.domain.kidapplication.repository.KidApplicationRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.common.ProductTime;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KidApplicationReviewService {

    private final KidApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    private final ClassroomCapacityService classroomCapacityService;
    private final KidApplicationWorkflowProperties workflowProperties;
    private final KidApplicationAdmissionService admissionService;
    private final KidApplicationNotificationService notificationService;
    private final KidApplicationAuditService auditService;
    private final DashboardService dashboardService;

    @Transactional
    public void approve(Long applicationId, ApproveKidApplicationRequest request, Long processorId) {
        KidApplication application = getApplicationForUpdate(applicationId);
        if (!application.isPending()) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_PENDING);
        }

        Member processor = getProcessor(processorId, application.getKindergarten().getId());
        Classroom classroom = resolveManagedClassroom(request.classroomId(), application.getKindergarten().getId());
        classroomCapacityService.validateSeatAvailable(classroom);

        Kid savedKid = admissionService.enrollKid(application, classroom, request.relationshipOrDefault());
        application.approveDirect(classroom, processor, savedKid.getId());

        admissionService.activateParent(application.getParent(), application.getKindergarten());
        notificationService.notifyParentAboutApproval(application.getParent(), application.getKidName(), application.getKindergarten());
        auditService.record(
                processor,
                DomainAuditAction.KID_APPLICATION_APPROVED,
                processor.getName() + "이(가) " + application.getKidName() + "의 입학을 승인했습니다.",
                Map.of(
                        "classroomId", classroom.getId(),
                        "kidId", savedKid.getId()
                ),
                application
        );
        dashboardService.evictDashboardStatisticsCache(application.getKindergarten().getId());
    }

    @Transactional
    public void placeOnWaitlist(Long applicationId, WaitlistKidApplicationRequest request, Long processorId) {
        KidApplication application = getApplicationForUpdate(applicationId);
        Member processor = getProcessor(processorId, application.getKindergarten().getId());
        Classroom classroom = resolveManagedClassroom(request.classroomId(), application.getKindergarten().getId());

        application.placeOnWaitlist(classroom, processor, request.decisionNote());
        notificationService.notifyParentAboutWaitlist(application.getParent(), application.getKidName(), classroom);
        auditService.record(
                processor,
                DomainAuditAction.KID_APPLICATION_WAITLISTED,
                processor.getName() + "이(가) " + application.getKidName() + "을(를) 대기열에 등록했습니다.",
                Map.of("classroomId", classroom.getId()),
                application
        );
    }

    @Transactional
    public void offer(Long applicationId, OfferKidApplicationRequest request, Long processorId) {
        KidApplication application = getApplicationForUpdate(applicationId);
        Member processor = getProcessor(processorId, application.getKindergarten().getId());
        Classroom classroom = resolveManagedClassroom(request.classroomId(), application.getKindergarten().getId());

        classroomCapacityService.validateSeatAvailable(classroom);

        LocalDateTime offerExpiresAt = ProductTime.nowDateTime().plus(workflowProperties.getOfferValidity());
        application.offerSeat(classroom, processor, offerExpiresAt, request.decisionNote());
        notificationService.notifyParentAboutOffer(application.getParent(), application.getKidName(), classroom, offerExpiresAt);
        auditService.record(
                processor,
                DomainAuditAction.KID_APPLICATION_OFFERED,
                processor.getName() + "이(가) " + application.getKidName() + "에게 입학 제안을 발송했습니다.",
                Map.of(
                        "classroomId", classroom.getId(),
                        "offerExpiresAt", offerExpiresAt.toString()
                ),
                application
        );
    }

    @Transactional
    public void acceptOffer(Long applicationId, AcceptKidApplicationOfferRequest request, Long parentId) {
        KidApplication application = getApplicationForUpdate(applicationId);

        if (!application.getParent().getId().equals(parentId)) {
            throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
        }
        if (!application.isOffered()) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_OFFERED);
        }
        if (application.getOfferExpiresAt() != null && !application.getOfferExpiresAt().isAfter(ProductTime.nowDateTime())) {
            application.markOfferExpired();
            notificationService.notifyParentAboutOfferExpired(application.getParent(), application.getKidName());
            throw new BusinessException(ErrorCode.APPLICATION_OFFER_EXPIRED);
        }

        Classroom classroom = classroomCapacityService.lockClassroom(application.getAssignedClassroom().getId());
        Kid savedKid = admissionService.enrollKid(application, classroom, request.relationshipOrDefault());
        application.acceptOffer(savedKid.getId());

        admissionService.activateParent(application.getParent(), application.getKindergarten());
        notificationService.notifyParentAboutApproval(application.getParent(), application.getKidName(), application.getKindergarten());
        notificationService.notifyStaffAboutOfferAccepted(classroom.getKindergarten(), application.getParent(), application.getKidName());
        auditService.record(
                application.getParent(),
                DomainAuditAction.KID_APPLICATION_OFFER_ACCEPTED,
                application.getParent().getName() + " 학부모가 " + application.getKidName() + "의 입학 제안을 수락했습니다.",
                Map.of(
                        "classroomId", classroom.getId(),
                        "kidId", savedKid.getId()
                ),
                application
        );
        dashboardService.evictDashboardStatisticsCache(application.getKindergarten().getId());
    }

    @Transactional
    public void reject(Long applicationId, RejectRequest request, Long processorId) {
        KidApplication application = getApplicationForUpdate(applicationId);
        Member processor = getProcessor(processorId, application.getKindergarten().getId());

        application.reject(request.reason(), processor);
        notificationService.notifyParentAboutRejection(application.getParent(), application.getKidName(), application.getKindergarten(), request.reason());
        auditService.record(
                processor,
                DomainAuditAction.KID_APPLICATION_REJECTED,
                processor.getName() + "이(가) " + application.getKidName() + "의 입학을 거절했습니다.",
                Map.of("reason", request.reason()),
                application
        );
    }

    private KidApplication getApplicationForUpdate(Long applicationId) {
        return applicationRepository.findByIdAndDeletedAtIsNullForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private Member getProcessor(Long processorId, Long kindergartenId) {
        return getStaffReviewer(processorId, kindergartenId);
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

    private Classroom resolveManagedClassroom(Long classroomId, Long kindergartenId) {
        Classroom classroom = classroomCapacityService.lockClassroom(classroomId);
        if (!classroom.getKindergarten().getId().equals(kindergartenId)) {
            throw new BusinessException(ErrorCode.CLASSROOM_NOT_BELONG_TO_KINDERGARTEN);
        }
        return classroom;
    }
}
