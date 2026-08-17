package com.kinderp.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 프로필 수정 요청
 */
@Getter
public class UpdateProfileRequest {

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 20, message = "이름은 2~20자여야 합니다")
    private String name;

    @Pattern(regexp = "^010-[0-9]{4}-[0-9]{4}$", message = "전화번호 형식이 올바르지 않습니다 (예: 010-1234-5678)")
    private String phone;
}
