package com.erp.global.security.access;

import com.erp.domain.announcement.entity.Announcement;
import com.erp.domain.attendance.entity.Attendance;
import com.erp.domain.attendance.entity.AttendanceChangeRequest;
import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.kid.entity.Kid;
import com.erp.domain.kid.repository.KidRepository;
import com.erp.domain.kidapplication.entity.KidApplication;
import com.erp.domain.kindergartenapplication.entity.KindergartenApplication;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.member.repository.MemberRepository;
import com.erp.domain.notepad.entity.Notepad;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹 요청자 기준의 접근 정책을 한 곳에서 관리한다.
 * 역할 검사와 별개로, 같은 유치원/본인 자녀 여부 같은 데이터 경계를 보장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessPolicyService {

    private final MemberRepository memberRepository;
    private final KidRepository kidRepository;

    public Member getRequester(Long memberId) {
        return memberRepository.findByIdWithKindergarten(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public void validateSameKindergarten(Member requester, Long targetKindergartenId) {
        if (!belongsToKindergarten(requester, targetKindergartenId)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }
    }

    public void validateStaffSameKindergarten(Member requester, Long targetKindergartenId) {
        if (!isStaff(requester) || !belongsToKindergarten(requester, targetKindergartenId)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }
    }

    public void validatePrincipalSameKindergarten(Member requester, Long targetKindergartenId) {
        if (requester == null
                || requester.getRole() != MemberRole.PRINCIPAL
                || !belongsToKindergarten(requester, targetKindergartenId)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }
    }

    public void validateKidReadAccess(Member requester, Kid kid) {
        if (kid == null) {
            throw new BusinessException(ErrorCode.KID_NOT_FOUND);
        }

        if (isStaff(requester)) {
            validateSameKindergarten(requester, kid.getClassroom().getKindergarten().getId());
            return;
        }

        if (requester.getRole() == MemberRole.PARENT && isParentOfKid(requester.getId(), kid.getId())) {
            return;
        }

        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    public void validateKidManageAccess(Member requester, Kid kid) {
        if (kid == null) {
            throw new BusinessException(ErrorCode.KID_NOT_FOUND);
        }
        validateStaffSameKindergarten(requester, kid.getClassroom().getKindergarten().getId());
    }

    public void validateClassroomReadAccess(Member requester, Classroom classroom) {
        if (classroom == null) {
            throw new BusinessException(ErrorCode.CLASSROOM_NOT_FOUND);
        }

        if (isStaff(requester)) {
            validateSameKindergarten(requester, classroom.getKindergarten().getId());
            return;
        }

        if (requester.getRole() == MemberRole.PARENT && isParentOfClassroom(requester.getId(), classroom.getId())) {
            return;
        }

        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    public void validateClassroomManageAccess(Member requester, Classroom classroom) {
        if (classroom == null) {
            throw new BusinessException(ErrorCode.CLASSROOM_NOT_FOUND);
        }
        validateStaffSameKindergarten(requester, classroom.getKindergarten().getId());
    }

    public void validateAttendanceReadAccess(Member requester, Attendance attendance) {
        if (attendance == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
        validateKidReadAccess(requester, attendance.getKid());
    }

    public void validateAttendanceManageAccess(Member requester, Kid kid) {
        validateKidManageAccess(requester, kid);
    }

    public void validateAttendanceChangeRequestCreateAccess(Member requester, Kid kid) {
        if (requester == null || requester.getRole() != MemberRole.PARENT || !isParentOfKid(requester.getId(), kid.getId())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED);
        }
    }

    public void validateAttendanceChangeRequestReadAccess(Member requester, AttendanceChangeRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND);
        }

        if (isStaff(requester)) {
            validateSameKindergarten(requester, request.getKindergartenId());
            return;
        }

        if (requester != null
                && requester.getRole() == MemberRole.PARENT
                && request.getRequester().getId().equals(requester.getId())) {
            return;
        }

        throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED);
    }

    public void validateAttendanceChangeRequestReviewAccess(Member requester, AttendanceChangeRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND);
        }
        if (!isStaff(requester)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED);
        }
        validateSameKindergarten(requester, request.getKindergartenId());
    }

    public void validateAttendanceChangeRequestCancelAccess(Member requester, AttendanceChangeRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_FOUND);
        }
        if (requester == null
                || requester.getRole() != MemberRole.PARENT
                || !request.getRequester().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED);
        }
    }

    public void validateAnnouncementReadAccess(Member requester, Announcement announcement) {
        if (announcement == null) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        validateSameKindergarten(requester, announcement.getKindergarten().getId());
    }

    public void validateNotepadReadAccess(Member requester, Notepad notepad) {
        if (notepad == null) {
            throw new BusinessException(ErrorCode.NOTEPAD_NOT_FOUND);
        }

        if (isStaff(requester)) {
            validateSameKindergarten(requester, resolveNotepadKindergartenId(notepad));
            return;
        }

        if (requester.getRole() != MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.NOTEPAD_ACCESS_DENIED);
        }

        if (notepad.isKidNotepad() && notepad.getKid() != null) {
            if (!isParentOfKid(requester.getId(), notepad.getKid().getId())) {
                throw new BusinessException(ErrorCode.NOTEPAD_ACCESS_DENIED);
            }
            return;
        }

        if (notepad.isClassroomNotepad() && notepad.getClassroom() != null) {
            if (!isParentOfClassroom(requester.getId(), notepad.getClassroom().getId())) {
                throw new BusinessException(ErrorCode.NOTEPAD_ACCESS_DENIED);
            }
            return;
        }

        validateSameKindergarten(requester, resolveNotepadKindergartenId(notepad));
    }

    public void validateNotepadManageAccess(Member requester, Notepad notepad) {
        if (notepad == null) {
            throw new BusinessException(ErrorCode.NOTEPAD_NOT_FOUND);
        }

        Long targetKindergartenId = resolveNotepadKindergartenId(notepad);
        if (requester.getRole() == MemberRole.PRINCIPAL && belongsToKindergarten(requester, targetKindergartenId)) {
            return;
        }

        if (requester.getRole() == MemberRole.TEACHER
                && belongsToKindergarten(requester, targetKindergartenId)
                && notepad.isWriter(requester)) {
            return;
        }

        throw new BusinessException(ErrorCode.NOTEPAD_ACCESS_DENIED);
    }

    public void validateNotificationReceiverAccess(Member requester, Member receiver) {
        if (receiver == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        validateStaffSameKindergarten(requester, requireKindergartenId(receiver));
    }

    public void validateKidApplicationReadAccess(Member requester, KidApplication application) {
        if (application == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        if (requester != null
                && requester.getRole() == MemberRole.PARENT
                && application.getParent().getId().equals(requester.getId())) {
            return;
        }

        if (isStaff(requester) && belongsToKindergarten(requester, application.getKindergarten().getId())) {
            return;
        }

        throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
    }

    public void validateKindergartenApplicationReadAccess(Member requester, KindergartenApplication application) {
        if (application == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        if (requester != null
                && requester.getRole() == MemberRole.TEACHER
                && application.getTeacher().getId().equals(requester.getId())) {
            return;
        }

        if (requester != null
                && requester.getRole() == MemberRole.PRINCIPAL
                && belongsToKindergarten(requester, application.getKindergarten().getId())) {
            return;
        }

        throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
    }

    private boolean belongsToKindergarten(Member requester, Long targetKindergartenId) {
        return requester != null
                && requester.getKindergarten() != null
                && targetKindergartenId != null
                && targetKindergartenId.equals(requester.getKindergarten().getId());
    }

    private boolean isStaff(Member requester) {
        return requester != null
                && (requester.getRole() == MemberRole.PRINCIPAL || requester.getRole() == MemberRole.TEACHER);
    }

    private boolean isParentOfKid(Long parentId, Long kidId) {
        return kidRepository.findParentKidByParentIdAndKidId(parentId, kidId).isPresent();
    }

    private boolean isParentOfClassroom(Long parentId, Long classroomId) {
        return kidRepository.findByParentId(parentId).stream()
                .anyMatch(kid -> kid.getClassroom() != null && classroomId.equals(kid.getClassroom().getId()));
    }

    private Long resolveNotepadKindergartenId(Notepad notepad) {
        if (notepad.getKid() != null && notepad.getKid().getClassroom() != null) {
            return notepad.getKid().getClassroom().getKindergarten().getId();
        }
        if (notepad.getClassroom() != null) {
            return notepad.getClassroom().getKindergarten().getId();
        }
        if (notepad.getWriter() != null) {
            return requireKindergartenId(notepad.getWriter());
        }
        throw new BusinessException(ErrorCode.NOTEPAD_ACCESS_DENIED);
    }

    private Long requireKindergartenId(Member member) {
        if (member == null || member.getKindergarten() == null) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }
        return member.getKindergarten().getId();
    }
}
