package com.kinderp.domain.kid.dto.response;

import com.kinderp.domain.kid.entity.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 원생 정보 응답 DTO
 */
public record KidResponse(
        Long id,
        Long classroomId,
        String classroomName,
        String name,
        LocalDate birthDate,
        int age,
        Gender gender,
        LocalDate admissionDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Kid 엔티티를 DTO로 변환
     */
    public static KidResponse from(com.kinderp.domain.kid.entity.Kid kid) {
        Long classroomId = null;
        String classroomName = null;
        if (kid.getClassroom() != null) {
            classroomId = kid.getClassroom().getId();
            classroomName = kid.getClassroom().getName();
        }

        return new KidResponse(
                kid.getId(),
                classroomId,
                classroomName,
                kid.getName(),
                kid.getBirthDate(),
                kid.getAge(),
                kid.getGender(),
                kid.getAdmissionDate(),
                kid.getCreatedAt(),
                kid.getUpdatedAt()
        );
    }
}
