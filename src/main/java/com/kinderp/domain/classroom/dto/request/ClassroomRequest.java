package com.kinderp.domain.classroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 반 등록/수정 요청 DTO
 */
@Getter
@NoArgsConstructor
public class ClassroomRequest {

    /**
     * 유치원 ID
     */
    @NotNull(message = "유치원 ID는 필수입니다")
    private Long kindergartenId;

    /**
     * 반 이름
     */
    @NotBlank(message = "반 이름은 필수입니다")
    @Size(max = 50, message = "반 이름은 50자 이하여야 합니다")
    private String name;

    /**
     * 연령대 (예: 5세반, 6세반, 7세반)
     */
    @Size(max = 20, message = "연령대는 20자 이하여야 합니다")
    private String ageGroup;

    /**
     * 담임 교사 ID
     */
    private Long teacherId;

    /**
     * 반 정원
     */
    private Integer capacity;

    public Integer capacityOrDefault() {
        return capacity == null ? com.kinderp.domain.classroom.entity.Classroom.DEFAULT_CAPACITY : capacity;
    }
}
