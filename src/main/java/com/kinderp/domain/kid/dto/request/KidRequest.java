package com.kinderp.domain.kid.dto.request;

import com.kinderp.domain.kid.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 원생 등록/수정 요청 DTO
 */
@Getter
@NoArgsConstructor
public class KidRequest {

    /**
     * 반 ID
     */
    @NotNull(message = "반 ID는 필수입니다")
    private Long classroomId;

    /**
     * 이름
     */
    @NotBlank(message = "이름은 필수입니다")
    private String name;

    /**
     * 생년월일
     */
    @NotNull(message = "생년월일은 필수입니다")
    private LocalDate birthDate;

    /**
     * 성별
     */
    @NotNull(message = "성별은 필수입니다")
    private Gender gender;

    /**
     * 입소일
     */
    @NotNull(message = "입소일은 필수입니다")
    private LocalDate admissionDate;
}
