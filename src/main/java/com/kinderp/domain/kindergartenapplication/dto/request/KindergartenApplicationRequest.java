package com.kinderp.domain.kindergartenapplication.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KindergartenApplicationRequest(
        @NotNull(message = "유치원 ID는 필수입니다")
        Long kindergartenId,

        @Size(max = 1000, message = "지원 메시지는 1000자 이하여야 합니다")
        String message
) {
}
