package com.kinderp.global.exception;

import lombok.Getter;

/**
 * 비즈니스 예외
 * 서비스 계층에서 던지는 커스텀 예외입니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 에러 코드
     */
    private final ErrorCode errorCode;

    /**
     * 기본 생성자
     *
     * @param errorCode 에러 코드
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 커스텀 메시지 생성자
     *
     * @param errorCode 에러 코드
     * @param message   커스텀 메시지
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 원인 예외를 포함한 생성자
     *
     * @param errorCode 에러 코드
     * @param cause     원인 예외
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
