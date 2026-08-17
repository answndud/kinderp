package com.kinderp.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청 DTO
 */
@Getter
@NoArgsConstructor
public class LoginRequest {

    /**
     * 이메일
     */
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    /**
     * 비밀번호
     */
    @NotBlank(message = "비밀번호는 필수입니다")
    private String password;
}
