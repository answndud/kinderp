package com.kinderp.domain.kidapplication.dto.request;

import com.kinderp.domain.kid.entity.Relationship;
import jakarta.validation.constraints.NotNull;

public record ApproveKidApplicationRequest(
        @NotNull(message = "반 배정은 필수입니다")
        Long classroomId,
        Relationship relationship
) {
    public Relationship relationshipOrDefault() {
        return relationship == null ? Relationship.FATHER : relationship;
    }
}
