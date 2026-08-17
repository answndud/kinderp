package com.kinderp.domain.kidapplication.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfferKidApplicationRequest(
        @NotNull(message = "반 배정은 필수입니다")
        Long classroomId,

        @Size(max = 500, message = "제안 메모는 500자 이하여야 합니다")
        String decisionNote
) {
}
