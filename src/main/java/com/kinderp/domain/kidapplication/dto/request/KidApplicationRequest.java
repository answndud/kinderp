package com.kinderp.domain.kidapplication.dto.request;

import com.kinderp.domain.kid.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record KidApplicationRequest(
        @NotNull(message = "유치원 ID는 필수입니다")
        Long kindergartenId,

        @NotBlank(message = "원생 이름은 필수입니다")
        @Size(max = 50, message = "원생 이름은 50자 이하여야 합니다")
        String kidName,

        @NotNull(message = "생년월일은 필수입니다")
        LocalDate birthDate,

        @NotNull(message = "성별은 필수입니다")
        Gender gender,

        Long preferredClassroomId,

        @Size(max = 1000, message = "특이사항은 1000자 이하여야 합니다")
        String notes
) {
}
