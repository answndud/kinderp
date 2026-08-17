package com.kinderp.domain.kid.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 반 배정 요청 DTO
 */
@Getter
@NoArgsConstructor
public class UpdateClassroomRequest {

    /**
     * 반 ID
     */
    @NotNull(message = "반 ID는 필수입니다")
    private Long classroomId;
}
