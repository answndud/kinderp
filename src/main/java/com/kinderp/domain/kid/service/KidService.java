package com.kinderp.domain.kid.service;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.service.ClassroomCapacityService;
import com.kinderp.domain.classroom.service.ClassroomService;
import com.kinderp.domain.kid.dto.request.AssignParentRequest;
import com.kinderp.domain.kid.dto.request.KidRequest;
import com.kinderp.domain.kid.dto.request.UpdateClassroomRequest;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.entity.ParentKid;
import com.kinderp.domain.kid.entity.Relationship;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 원생 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KidService {

    private final KidRepository kidRepository;
    private final ClassroomService classroomService;
    private final ClassroomCapacityService classroomCapacityService;
    private final MemberService memberService;
    private final AccessPolicyService accessPolicyService;

    @Transactional
    public Long createKid(KidRequest request, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        Classroom classroom = classroomCapacityService.lockClassroom(request.getClassroomId());
        accessPolicyService.validateClassroomManageAccess(requester, classroom);
        classroomCapacityService.validateSeatAvailable(classroom);

        Kid kid = Kid.create(
                classroom,
                request.getName(),
                request.getBirthDate(),
                request.getGender(),
                request.getAdmissionDate()
        );

        Kid saved = kidRepository.save(kid);
        return saved.getId();
    }

    /**
     * 원생 조회
     */
    public Kid getKid(Long id) {
        return kidRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KID_NOT_FOUND));
    }

    public Kid getKid(Long id, Long requesterId) {
        Kid kid = getKid(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidReadAccess(requester, kid);
        return kid;
    }

    public List<Kid> getKidsByClassroom(Long classroomId, Long requesterId) {
        Classroom classroom = classroomService.getClassroom(classroomId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateClassroomReadAccess(requester, classroom);
        return kidRepository.findByClassroomIdAndDeletedAtIsNull(classroomId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Kid> getKidsByClassroom(Long classroomId,
                                                                         org.springframework.data.domain.Pageable pageable,
                                                                         Long requesterId) {
        Classroom classroom = classroomService.getClassroom(classroomId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateClassroomReadAccess(requester, classroom);
        return kidRepository.findByClassroomIdAndDeletedAtIsNull(classroomId, pageable);
    }

    public List<Kid> getKidsByKindergarten(Long kindergartenId, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return kidRepository.findByKindergartenIdAndDeletedAtIsNull(kindergartenId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Kid> getKidsByKindergarten(Long kindergartenId,
                                                                            org.springframework.data.domain.Pageable pageable,
                                                                            Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return kidRepository.findByKindergartenIdAndDeletedAtIsNull(kindergartenId, pageable);
    }

    public List<Kid> searchKidsByName(Long classroomId, String name, Long requesterId) {
        Classroom classroom = classroomService.getClassroom(classroomId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateClassroomReadAccess(requester, classroom);
        return kidRepository.findByClassroomIdAndNameContaining(classroomId, name);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Kid> searchKidsByName(Long classroomId,
                                                                       String name,
                                                                       org.springframework.data.domain.Pageable pageable,
                                                                       Long requesterId) {
        Classroom classroom = classroomService.getClassroom(classroomId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateClassroomReadAccess(requester, classroom);
        return kidRepository.findByClassroomIdAndNameContaining(classroomId, name, pageable);
    }

    public List<Kid> searchKidsByKindergarten(Long kindergartenId, String name, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return kidRepository.findByKindergartenIdAndNameContaining(kindergartenId, name);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Kid> searchKidsByKindergarten(Long kindergartenId,
                                                                              String name,
                                                                              org.springframework.data.domain.Pageable pageable,
                                                                              Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return kidRepository.findByKindergartenIdAndNameContaining(kindergartenId, name, pageable);
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> getClassroomCounts(Long kindergartenId, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return loadClassroomCounts(kindergartenId);
    }

    /**
     * 학부모의 원생 목록 조회
     */
    public List<Kid> getKidsByParent(Long parentId) {
        // 학부모 존재 확인
        Member parent = memberService.getMemberById(parentId);

        // 학부모 역할 확인
        if (parent.getRole() != com.kinderp.domain.member.entity.MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return kidRepository.findByParentId(parentId);
    }

    @Transactional
    public void updateKid(Long id, String name, java.time.LocalDate birthDate, Gender gender, Long requesterId) {
        Kid kid = getKid(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidManageAccess(requester, kid);
        kid.update(name, birthDate, gender);
    }

    @Transactional
    public void updateClassroom(Long id, UpdateClassroomRequest request, Long requesterId) {
        Kid kid = getKid(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidManageAccess(requester, kid);

        Classroom classroom = classroomCapacityService.lockClassroom(request.getClassroomId());
        accessPolicyService.validateClassroomManageAccess(requester, classroom);
        if (!classroom.getId().equals(kid.getClassroom().getId())) {
            classroomCapacityService.validateSeatAvailable(classroom);
        }
        kid.assignClassroom(classroom);
    }

    @Transactional
    public void assignParent(Long kidId, AssignParentRequest request, Long requesterId) {
        Kid kid = getKid(kidId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidManageAccess(requester, kid);

        Member parent = memberService.getMemberById(request.getParentId());
        if (parent.getRole() != com.kinderp.domain.member.entity.MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        accessPolicyService.validateSameKindergarten(parent, kid.getClassroom().getKindergarten().getId());

        if (kid.hasParent(parent)) {
            throw new BusinessException(ErrorCode.PARENT_KID_RELATION_EXISTS);
        }

        kid.addParent(parent, request.getRelationship());
    }

    @Transactional
    public void removeParent(Long kidId, Long parentId, Long requesterId) {
        Kid kid = getKid(kidId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidManageAccess(requester, kid);

        Member parent = memberService.getMemberById(parentId);
        if (!kid.hasParent(parent)) {
            throw new BusinessException(ErrorCode.PARENT_KID_RELATION_NOT_FOUND);
        }

        kid.removeParent(parent);
    }

    @Transactional
    public void deleteKid(Long id, Long requesterId) {
        Kid kid = getKid(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidManageAccess(requester, kid);
        kid.softDelete();
    }

    @Transactional(readOnly = true)
    public com.kinderp.domain.kid.dto.response.KidDetailResponse getKidDetail(Long id, Long requesterId) {
        Kid kid = getKid(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidReadAccess(requester, kid);
        List<ParentKid> parentKids = kidRepository.findParentsByKidId(id);
        return com.kinderp.domain.kid.dto.response.KidDetailResponse.from(kid, parentKids);
    }

    private java.util.Map<Long, Long> loadClassroomCounts(Long kindergartenId) {
        java.util.List<Object[]> rows = kidRepository.countByKindergartenGroupedByClassroom(kindergartenId);
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        for (Object[] row : rows) {
            Long classroomId = (Long) row[0];
            Long count = (Long) row[1];
            result.put(classroomId, count);
        }
        return result;
    }
}
