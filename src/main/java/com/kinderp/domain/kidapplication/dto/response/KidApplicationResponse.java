package com.kinderp.domain.kidapplication.dto.response;

import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kidapplication.entity.ApplicationStatus;
import com.kinderp.domain.kidapplication.entity.KidApplication;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record KidApplicationResponse(
        Long id,
        ParentInfo parent,
        KindergartenInfo kindergarten,
        String kidName,
        LocalDate birthDate,
        Gender gender,
        ClassroomInfo preferredClassroom,
        ClassroomInfo assignedClassroom,
        ApplicationStatus status,
        String notes,
        String decisionNote,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        LocalDateTime waitlistedAt,
        LocalDateTime offeredAt,
        LocalDateTime offerExpiresAt,
        LocalDateTime offerAcceptedAt,
        String rejectionReason,
        Long kidId
) {
    public static KidApplicationResponse from(KidApplication application) {
        ClassroomInfo classroomInfo = null;
        if (application.getPreferredClassroom() != null) {
            classroomInfo = new ClassroomInfo(
                    application.getPreferredClassroom().getId(),
                    application.getPreferredClassroom().getName()
            );
        }

        ClassroomInfo assignedClassroomInfo = null;
        if (application.getAssignedClassroom() != null) {
            assignedClassroomInfo = new ClassroomInfo(
                    application.getAssignedClassroom().getId(),
                    application.getAssignedClassroom().getName()
            );
        }

        return new KidApplicationResponse(
                application.getId(),
                new ParentInfo(application.getParent().getId(), application.getParent().getName()),
                new KindergartenInfo(application.getKindergarten().getId(), application.getKindergarten().getName()),
                application.getKidName(),
                application.getBirthDate(),
                application.getGender(),
                classroomInfo,
                assignedClassroomInfo,
                application.getStatus(),
                application.getNotes(),
                application.getDecisionNote(),
                application.getCreatedAt(),
                application.getProcessedAt(),
                application.getWaitlistedAt(),
                application.getOfferedAt(),
                application.getOfferExpiresAt(),
                application.getOfferAcceptedAt(),
                application.getRejectionReason(),
                application.getKidId()
        );
    }

    public record ParentInfo(Long id, String name) {
    }

    public record KindergartenInfo(Long id, String name) {
    }

    public record ClassroomInfo(Long id, String name) {
    }
}
