package com.kinderp.global.common;

import com.kinderp.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * API 공통 응답 클래스
 * 모든 API 응답은 이 형식을 따릅니다.
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 응답 성공 여부
     */
    private boolean success;

    /**
     * 응답 데이터
     */
    private T data;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 에러 코드
     */
    private String code;

    // ========== 성공 응답 ==========

    /**
     * 데이터가 있는 성공 응답
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        return response;
    }

    /**
     * 데이터가 없는 성공 응답
     */
    public static ApiResponse<Void> success() {
        ApiResponse<Void> response = new ApiResponse<>();
        response.success = true;
        return response;
    }

    /**
     * 메시지가 포함된 성공 응답
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.message = message;
        return response;
    }

    // ========== 실패 응답 ==========

    /**
     * 에러 코드로 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = errorCode.getMessage();
        response.code = errorCode.getCode();
        return response;
    }

    /**
     * 에러 코드와 커스텀 메시지로 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = message;
        response.code = errorCode.getCode();
        return response;
    }

    /**
     * 에러 코드와 데이터로 실패 응답 생성 (검증 오류 등)
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = errorCode.getMessage();
        response.code = errorCode.getCode();
        response.data = data;
        return response;
    }
}
