package com.kinderp.domain.kid.dto.response;

import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.ParentKid;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 원생 상세 정보 응답 DTO (학부모 정보 포함)
 */
public record KidDetailResponse(
        Long id,
        Long classroomId,
        String classroomName,
        String name,
        LocalDate birthDate,
        int age,
        Gender gender,
        LocalDate admissionDate,
        List<ParentInfo> parents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 학부모 정보
     */
    public record ParentInfo(
            Long parentId,
            String parentName,
            String relationship
    ) {
        public static ParentInfo from(ParentKid parentKid) {
            return new ParentInfo(
                    parentKid.getParent().getId(),
                    parentKid.getParent().getName(),
                    parentKid.getRelationship().getDescription()
            );
        }
    }

    /**
     * Kid 엔티티를 DTO로 변환
     */
    public static KidDetailResponse from(com.kinderp.domain.kid.entity.Kid kid, List<ParentKid> parentKids) {
        Long classroomId = null;
        String classroomName = null;
        if (kid.getClassroom() != null) {
            classroomId = kid.getClassroom().getId();
            classroomName = kid.getClassroom().getName();
        }

        List<ParentInfo> parentInfos = parentKids.stream()
                .map(ParentInfo::from)
                .toList();

        return new KidDetailResponse(
                kid.getId(),
                classroomId,
                classroomName,
                kid.getName(),
                kid.getBirthDate(),
                kid.getAge(),
                kid.getGender(),
                kid.getAdmissionDate(),
                parentInfos,
                kid.getCreatedAt(),
                kid.getUpdatedAt()
        );
    }
}
