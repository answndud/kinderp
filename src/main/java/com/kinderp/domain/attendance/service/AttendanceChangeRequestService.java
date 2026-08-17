package com.kinderp.domain.attendance.service;

import com.kinderp.domain.attendance.dto.request.AttendanceChangeRequestCreateRequest;
import com.kinderp.domain.attendance.dto.request.AttendanceChangeRequestRejectRequest;
import com.kinderp.domain.attendance.dto.request.AttendanceRequest;
import com.kinderp.domain.attendance.dto.response.AttendanceChangeRequestResponse;
import com.kinderp.domain.classroom.service.ClassroomService;
import com.kinderp.domain.attendance.repository.AttendanceChangeRequestRepository;
import com.kinderp.domain.attendance.repository.AttendanceRepository;
import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.domainaudit.service.DomainAuditLogService;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.service.KidService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceChangeRequestService {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final KidService kidService;
    private final ClassroomService classroomService;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final AccessPolicyService accessPolicyService;
    private final DomainAuditLogService domainAuditLogService;

    @Transactional
    public Long create(AttendanceChangeRequestCreateRequest request, Long requesterId, String idempotencyKey) {
        Member requester = memberRepository.findByIdWithKindergartenForUpdate(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (requester.getRole() != MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED);
        }

        Kid kid = kidService.getKid(request.kidId());
        accessPolicyService.validateAttendanceChangeRequestCreateAccess(requester, kid);

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            var existing = attendanceChangeRequestRepository
                    .findByRequesterIdAndIdempotencyKey(requesterId, normalizedIdempotencyKey);
            if (existing.isPresent()) {
                if (matchesIdempotentPayload(existing.get(), request)) {
                    return existing.get().getId();
                }
                throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_IDEMPOTENCY_KEY_REUSED);
            }
        }

        if (attendanceChangeRequestRepository.existsByKidIdAndDateAndStatus(
                request.kidId(),
                request.date(),
                com.kinderp.domain.attendance.entity.AttendanceChangeRequestStatus.PENDING
        )) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ALREADY_PENDING);
        }

        var changeRequest = com.kinderp.domain.attendance.entity.AttendanceChangeRequest.create(
                kid,
                requester,
                request.date(),
                request.status(),
                request.dropOffTime(),
                request.pickUpTime(),
                request.note(),
                normalizedIdempotencyKey
        );
        var saved = savePendingRequest(changeRequest);

        notifyStaffAboutSubmission(kid, requester, request.date(), request.status());
        domainAuditLogService.record(
                requester,
                changeRequest.getKindergartenId(),
                DomainAuditAction.ATTENDANCE_CHANGE_REQUEST_SUBMITTED,
                DomainAuditTargetType.ATTENDANCE_CHANGE_REQUEST,
                saved.getId(),
                requester.getName() + " 학부모가 " + kid.getName() + "의 출결 변경을 요청했습니다.",
                Map.of(
                        "kidId", kid.getId(),
                        "date", request.date().toString(),
                        "requestedStatus", request.status().name()
                )
        );

        return saved.getId();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Idempotency-Key는 100자 이하여야 합니다");
        }
        return normalized;
    }

    private boolean matchesIdempotentPayload(
            com.kinderp.domain.attendance.entity.AttendanceChangeRequest existing,
            AttendanceChangeRequestCreateRequest request
    ) {
        return existing.getKid().getId().equals(request.kidId())
                && existing.getDate().equals(request.date())
                && existing.getRequestedStatus() == request.status()
                && java.util.Objects.equals(existing.getRequestedDropOffTime(), request.dropOffTime())
                && java.util.Objects.equals(existing.getRequestedPickUpTime(), request.pickUpTime())
                && java.util.Objects.equals(existing.getNote(), request.note());
    }

    private com.kinderp.domain.attendance.entity.AttendanceChangeRequest savePendingRequest(
            com.kinderp.domain.attendance.entity.AttendanceChangeRequest changeRequest
    ) {
        try {
            return attendanceChangeRequestRepository.saveAndFlush(changeRequest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ALREADY_PENDING);
        }
    }

    public List<AttendanceChangeRequestResponse> getMyRequests(Long requesterId) {
        return attendanceChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId)
                .stream()
                .map(AttendanceChangeRequestResponse::from)
                .toList();
    }

    public List<AttendanceChangeRequestResponse> getPendingRequests(Long requesterId, Long classroomId, LocalDate date) {
        Member reviewer = accessPolicyService.getRequester(requesterId);
        if (reviewer.getRole() != MemberRole.PRINCIPAL && reviewer.getRole() != MemberRole.TEACHER) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED);
        }
        if (reviewer.getKindergarten() == null) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }

        if (classroomId != null) {
            accessPolicyService.validateClassroomReadAccess(reviewer, classroomService.getClassroom(classroomId));
        }

        return attendanceChangeRequestRepository.findPendingRequests(
                        reviewer.getKindergarten().getId(),
                        com.kinderp.domain.attendance.entity.AttendanceChangeRequestStatus.PENDING,
                        classroomId,
                        date
                ).stream()
                .map(AttendanceChangeRequestResponse::from)
                .toList();
    }

    public AttendanceChangeRequestResponse getRequest(Long id, Long requesterId) {
        var request = attendanceChangeRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND));
        accessPolicyService.validateAttendanceChangeRequestReadAccess(accessPolicyService.getRequester(requesterId), request);
        return AttendanceChangeRequestResponse.from(request);
    }

    @Transactional
    public void approve(Long id, Long reviewerId) {
        var changeRequest = attendanceChangeRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND));
        Member reviewer = accessPolicyService.getRequester(reviewerId);
        accessPolicyService.validateAttendanceChangeRequestReviewAccess(reviewer, changeRequest);

        AttendanceRequest attendanceRequest = new AttendanceRequest(
                changeRequest.getKid().getId(),
                changeRequest.getDate(),
                changeRequest.getRequestedStatus(),
                changeRequest.getRequestedDropOffTime(),
                changeRequest.getRequestedPickUpTime(),
                changeRequest.getNote()
        );

        attendanceService.upsertAttendance(attendanceRequest, reviewerId);
        Long attendanceId = attendanceRepository.findByKidIdAndDate(changeRequest.getKid().getId(), changeRequest.getDate())
                .map(com.kinderp.domain.attendance.entity.Attendance::getId)
                .orElse(null);

        changeRequest.approve(reviewer, attendanceId);
        notifyParentAboutApproval(changeRequest);
        domainAuditLogService.record(
                reviewer,
                changeRequest.getKindergartenId(),
                DomainAuditAction.ATTENDANCE_CHANGE_REQUEST_APPROVED,
                DomainAuditTargetType.ATTENDANCE_CHANGE_REQUEST,
                changeRequest.getId(),
                reviewer.getName() + "이(가) " + changeRequest.getKid().getName() + "의 출결 요청을 승인했습니다.",
                Map.of(
                        "kidId", changeRequest.getKid().getId(),
                        "date", changeRequest.getDate().toString(),
                        "requestedStatus", changeRequest.getRequestedStatus().name()
                )
        );
    }

    @Transactional
    public void reject(Long id, AttendanceChangeRequestRejectRequest request, Long reviewerId) {
        var changeRequest = attendanceChangeRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND));
        Member reviewer = accessPolicyService.getRequester(reviewerId);
        accessPolicyService.validateAttendanceChangeRequestReviewAccess(reviewer, changeRequest);

        changeRequest.reject(reviewer, request.reason());
        notifyParentAboutRejection(changeRequest);
        domainAuditLogService.record(
                reviewer,
                changeRequest.getKindergartenId(),
                DomainAuditAction.ATTENDANCE_CHANGE_REQUEST_REJECTED,
                DomainAuditTargetType.ATTENDANCE_CHANGE_REQUEST,
                changeRequest.getId(),
                reviewer.getName() + "이(가) " + changeRequest.getKid().getName() + "의 출결 요청을 거절했습니다.",
                Map.of(
                        "kidId", changeRequest.getKid().getId(),
                        "date", changeRequest.getDate().toString(),
                        "reason", request.reason()
                )
        );
    }

    @Transactional
    public void cancel(Long id, Long requesterId) {
        var changeRequest = attendanceChangeRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND));
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateAttendanceChangeRequestCancelAccess(requester, changeRequest);

        changeRequest.cancel();
        domainAuditLogService.record(
                requester,
                changeRequest.getKindergartenId(),
                DomainAuditAction.ATTENDANCE_CHANGE_REQUEST_CANCELLED,
                DomainAuditTargetType.ATTENDANCE_CHANGE_REQUEST,
                changeRequest.getId(),
                requester.getName() + " 학부모가 " + changeRequest.getKid().getName() + "의 출결 요청을 취소했습니다.",
                Map.of(
                        "kidId", changeRequest.getKid().getId(),
                        "date", changeRequest.getDate().toString()
                )
        );
    }

    private void notifyStaffAboutSubmission(Kid kid, Member requester, LocalDate date, com.kinderp.domain.attendance.entity.AttendanceStatus requestedStatus) {
        String content = requester.getName() + " 학부모님이 " + kid.getName() + "의 " + date + " 출결을 "
                + requestedStatus.getDescription() + " 상태로 변경 요청했습니다.";

        java.util.List<Long> receiverIds = new java.util.ArrayList<>();
        if (kid.getClassroom().getTeacher() != null) {
            receiverIds.add(kid.getClassroom().getTeacher().getId());
        }
        memberRepository.findByKindergartenIdAndRole(kid.getClassroom().getKindergarten().getId(), MemberRole.PRINCIPAL)
                .ifPresent(principal -> receiverIds.add(principal.getId()));

        if (!receiverIds.isEmpty()) {
            notificationService.notifyWithLink(
                    receiverIds,
                    NotificationType.ATTENDANCE_CHANGE_REQUEST_SUBMITTED,
                    "새 출결 변경 요청",
                    content,
                    "/attendance-requests"
            );
        }
    }

    private void notifyParentAboutApproval(com.kinderp.domain.attendance.entity.AttendanceChangeRequest request) {
        notificationService.notifyWithLink(
                request.getRequester().getId(),
                NotificationType.ATTENDANCE_CHANGE_REQUEST_APPROVED,
                "출결 요청 승인",
                request.getKid().getName() + "의 " + request.getDate() + " 출결 변경 요청이 승인되었습니다.",
                "/attendance-requests"
        );
    }

    private void notifyParentAboutRejection(com.kinderp.domain.attendance.entity.AttendanceChangeRequest request) {
        notificationService.notifyWithLink(
                request.getRequester().getId(),
                NotificationType.ATTENDANCE_CHANGE_REQUEST_REJECTED,
                "출결 요청 거절",
                request.getKid().getName() + "의 " + request.getDate() + " 출결 변경 요청이 거절되었습니다. 사유: " + request.getRejectionReason(),
                "/attendance-requests"
        );
    }
}
