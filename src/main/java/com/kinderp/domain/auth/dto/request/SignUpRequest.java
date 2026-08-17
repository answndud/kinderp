package com.kinderp.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 요청 DTO
 */
@Getter
@NoArgsConstructor
public class SignUpRequest {

    /**
     * 이메일 (로그인 ID)
     */
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    /**
     * 비밀번호 (8자 이상, 영문+숫자+특수문자 조합)
     */
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 영문, 숫자, 특수문자 조합이어야 합니다")
    private String password;

    /**
     * 비밀번호 확인
     */
    @NotBlank(message = "비밀번호 확인은 필수입니다")
    private String passwordConfirm;

    /**
     * 이름
     */
    @NotBlank(message = "이름은 필수입니다")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다")
    private String name;

    /**
     * 전화번호
     */
    @Pattern(regexp = "^\\d{10,11}$", message = "전화번호 형식이 올바르지 않습니다")
    private String phone;

    /**
     * 역할 (PRINCIPAL, TEACHER, PARENT)
     */
    @NotBlank(message = "역할은 필수입니다")
    private String role;
}
