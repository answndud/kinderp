package com.kinderp.domain.kidapplication.dto.request;

import jakarta.validation.constraints.Size;

public record RejectRequest(
        @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다")
        String reason
) {
}
