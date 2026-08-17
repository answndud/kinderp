package com.kinderp.domain.kindergartenapplication.dto.response;

import com.kinderp.domain.kindergartenapplication.entity.ApplicationStatus;
import com.kinderp.domain.kindergartenapplication.entity.KindergartenApplication;

import java.time.LocalDateTime;

public record KindergartenApplicationResponse(
        Long id,
        TeacherInfo teacher,
        KindergartenInfo kindergarten,
        ApplicationStatus status,
        String message,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        String rejectionReason
) {
    public static KindergartenApplicationResponse from(KindergartenApplication application) {
        return new KindergartenApplicationResponse(
                application.getId(),
                new TeacherInfo(application.getTeacher().getId(), application.getTeacher().getName(), application.getTeacher().getEmail()),
                new KindergartenInfo(application.getKindergarten().getId(), application.getKindergarten().getName()),
                application.getStatus(),
                application.getMessage(),
                application.getCreatedAt(),
                application.getProcessedAt(),
                application.getRejectionReason()
        );
    }

    public record TeacherInfo(Long id, String name, String email) {
    }

    public record KindergartenInfo(Long id, String name) {
    }
}
