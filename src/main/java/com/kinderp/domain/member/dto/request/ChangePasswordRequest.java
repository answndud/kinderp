package com.kinderp.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 비밀번호 변경 요청
 */
@Getter
public class ChangePasswordRequest {

    @NotBlank(message = "현재 비밀번호는 필수입니다")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다")
    private String newPassword;
}
