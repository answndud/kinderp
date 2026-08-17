package com.kinderp.domain.kindergarten.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 유치원 등록/수정 요청 DTO
 */
@Getter
@NoArgsConstructor
public class KindergartenRequest {

    /**
     * 유치원명
     */
    @NotBlank(message = "유치원명은 필수입니다")
    @Size(max = 100, message = "유치원명은 100자 이하여야 합니다")
    private String name;

    /**
     * 주소
     */
    @Size(max = 255, message = "주소는 255자 이하여야 합니다")
    private String address;

    /**
     * 전화번호
     */
    @Pattern(regexp = "^\\d{9,11}$", message = "전화번호 형식이 올바르지 않습니다")
    private String phone;

    /**
     * 오픈 시간 (HH:mm 형식)
     */
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "시간 형식이 올바르지 않습니다")
    private String openTime;

    /**
     * 종료 시간 (HH:mm 형식)
     */
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "시간 형식이 올바르지 않습니다")
    private String closeTime;

    /**
     * 문자열 시간을 LocalTime으로 변환
     */
    public LocalTime getOpenTimeAsLocalTime() {
        return openTime != null ? LocalTime.parse(openTime) : null;
    }

    /**
     * 문자열 시간을 LocalTime으로 변환
     */
    public LocalTime getCloseTimeAsLocalTime() {
        return closeTime != null ? LocalTime.parse(closeTime) : null;
    }
}
