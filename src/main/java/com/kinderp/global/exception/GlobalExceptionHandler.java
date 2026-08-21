package com.kinderp.global.exception;

import com.kinderp.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.time.DateTimeException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 * 모든 예외를 일관된 형식으로 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        if (e.getErrorCode().getStatus() >= 500) {
            log.error("BusinessException: {}", e.getMessage(), e);
        } else {
            log.warn("BusinessException: {}", e.getMessage());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getErrorCode().getStatus());
        if (e.getErrorCode() == ErrorCode.AUTH_RATE_LIMITED
                || e.getErrorCode() == ErrorCode.EXTERNAL_API_RATE_LIMITED) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
        }
        return response.body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * @Valid 검증 예외 처리 (Request Body)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        log.error("ValidationException: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    /**
     * 필수 query parameter 누락 예외 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        log.warn("MissingRequestParameter: {}", e.getMessage());

        Map<String, String> errors = Map.of(e.getParameterName(), "필수 요청 파라미터입니다");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    /**
     * query/path parameter 타입 변환 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatch: {}", e.getMessage());

        Map<String, String> errors = Map.of(e.getName(), "요청 파라미터 타입이 올바르지 않습니다");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    /**
     * @Validated query/path parameter 제약 위반 예외 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("ConstraintViolation: {}", e.getMessage());

        Map<String, String> errors = e.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (left, right) -> left
                ));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    /**
     * 날짜/시간 파라미터 값 범위 예외 처리
     */
    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<ApiResponse<?>> handleDateTimeException(DateTimeException e) {
        log.warn("DateTimeException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    /**
     * @Valid 검증 예외 처리 (Model Attribute)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException e) {
        log.error("BindException: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    /**
     * 지원하지 않는 HTTP 메서드 예외 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.error("MethodNotSupported: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 접근 권한 예외 처리
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(Exception e) {
        log.warn("AccessDenied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED));
    }

    /**
     * 정적 리소스를 찾을 수 없는 경우 (Chrome DevTools 등)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        
        // .well-known 등 브라우저 자동 요청은 디버그 레벨로만 기록
        if (uri.contains(".well-known") || uri.contains("devtools")) {
            log.debug("Browser auto-request ignored: {}", uri);
        } else {
            log.warn("Resource not found: {}", uri);
        }
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.ENTITY_NOT_FOUND));
    }

    /**
     * 그 외 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e, HttpServletRequest request) {
        // 특정 경로의 에러는 로그 레벨 조정
        String uri = request.getRequestURI();
        if (uri.contains(".well-known") || uri.contains("devtools")) {
            log.debug("Exception in browser auto-request: {}", e.getMessage());
        } else {
            log.error("Exception: {}", e.getMessage(), e);
        }
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
