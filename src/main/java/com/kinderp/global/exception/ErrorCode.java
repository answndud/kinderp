package com.kinderp.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 enum
 * HTTP 상태 코드, 비즈니스 코드, 메시지를 포함합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== Common ==========
    INVALID_INPUT_VALUE(400, "C001", "잘못된 입력값입니다"),
    ENTITY_NOT_FOUND(404, "C002", "엔티티를 찾을 수 없습니다"),
    INTERNAL_SERVER_ERROR(500, "C003", "서버 오류가 발생했습니다"),
    METHOD_NOT_ALLOWED(405, "C004", "지원하지 않는 메서드입니다"),
    EXTERNAL_API_RATE_LIMITED(429, "C005", "외부 서비스 요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),

    // ========== Auth ==========
    INVALID_CREDENTIALS(401, "A001", "이메일 또는 비밀번호가 잘못되었습니다"),
    TOKEN_EXPIRED(401, "A002", "토큰이 만료되었습니다"),
    TOKEN_INVALID(401, "A003", "유효하지 않은 토큰입니다"),
    ACCESS_DENIED(403, "A004", "접근 권한이 없습니다"),
    REFRESH_TOKEN_NOT_FOUND(404, "A005", "리프레시 토큰을 찾을 수 없습니다"),
    AUTH_RATE_LIMITED(429, "A006", "인증 요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
    SOCIAL_ACCOUNT_ALREADY_LINKED(409, "A007", "이미 다른 계정에 연결된 소셜 계정입니다"),
    SOCIAL_PROVIDER_SLOT_OCCUPIED(409, "A008", "이미 동일 제공자의 다른 소셜 계정이 연결되어 있습니다"),
    SOCIAL_ACCOUNT_NOT_LINKED(404, "A009", "연결된 소셜 계정을 찾을 수 없습니다"),
    SOCIAL_UNLINK_REQUIRES_LOCAL_PASSWORD(400, "A010", "다른 로그인 수단을 먼저 확보해야 소셜 연결을 해제할 수 있습니다"),
    SOCIAL_PROVIDER_REPLACEMENT_NOT_ALLOWED(409, "A011", "같은 제공자의 소셜 계정은 다른 계정으로 교체할 수 없습니다"),

    // ========== Member ==========
    EMAIL_ALREADY_EXISTS(409, "M001", "이미 사용 중인 이메일입니다"),
    MEMBER_NOT_FOUND(404, "M002", "회원을 찾을 수 없습니다"),
    MEMBER_WITHDRAWN(400, "M003", "이미 탈퇴한 회원입니다"),
    PASSWORD_MISMATCH(400, "M004", "비밀번호가 일치하지 않습니다"),
    PASSWORD_ALREADY_SET(409, "M005", "이미 로컬 비밀번호가 설정되어 있습니다"),

    // ========== Kindergarten ==========
    KINDERGARTEN_NOT_FOUND(404, "K001", "유치원을 찾을 수 없습니다"),
    KINDERGARTEN_ALREADY_EXISTS(409, "K002", "이미 등록된 유치원입니다"),

    // ========== Classroom ==========
    CLASSROOM_NOT_FOUND(404, "CL001", "반을 찾을 수 없습니다"),
    CLASSROOM_ALREADY_HAS_TEACHER(400, "CL002", "이미 담임 교사가 배정된 반입니다"),
    CLASSROOM_HAS_KIDS(400, "CL003", "원생이 있는 반은 삭제할 수 없습니다"),
    CLASSROOM_NOT_BELONG_TO_KINDERGARTEN(400, "CL004", "해당 유치원의 반이 아닙니다"),
    CLASSROOM_CAPACITY_EXCEEDED(409, "CL005", "반 정원을 초과할 수 없습니다"),
    CLASSROOM_CAPACITY_REDUCTION_NOT_ALLOWED(400, "CL006", "현재 배정 인원보다 정원을 작게 줄일 수 없습니다"),

    // ========== Kid ==========
    KID_NOT_FOUND(404, "KD001", "원생을 찾을 수 없습니다"),
    PARENT_KID_RELATION_EXISTS(409, "KD002", "이미 연결된 학부모-원생 관계입니다"),
    PARENT_KID_RELATION_NOT_FOUND(404, "KD003", "학부모-원생 연결을 찾을 수 없습니다"),

    // ========== Attendance ==========
    ATTENDANCE_ALREADY_EXISTS(409, "AT001", "이미 출석 정보가 존재합니다"),
    ATTENDANCE_NOT_FOUND(404, "AT002", "출석 정보를 찾을 수 없습니다"),
    INVALID_ATTENDANCE_STATUS(400, "AT003", "잘못된 출석 상태입니다"),
    ATTENDANCE_CHANGE_REQUEST_NOT_FOUND(404, "AT004", "출결 요청을 찾을 수 없습니다"),
    ATTENDANCE_CHANGE_REQUEST_ALREADY_PENDING(409, "AT005", "같은 날짜의 처리 대기 출결 요청이 이미 있습니다"),
    ATTENDANCE_CHANGE_REQUEST_NOT_PENDING(400, "AT006", "처리 가능한 출결 요청 상태가 아닙니다"),
    ATTENDANCE_CHANGE_REQUEST_ACCESS_DENIED(403, "AT007", "출결 요청 접근 권한이 없습니다"),
    ATTENDANCE_CHANGE_REQUEST_IDEMPOTENCY_KEY_REUSED(409, "AT008", "멱등 키를 다른 요청에 재사용할 수 없습니다"),

    // ========== Notepad ==========
    NOTEPAD_NOT_FOUND(404, "N001", "알림장을 찾을 수 없습니다"),
    NOTEPAD_ACCESS_DENIED(403, "N002", "알림장 조회 권한이 없습니다"),

    // ========== Announcement ==========
    ANNOUNCEMENT_NOT_FOUND(404, "AN001", "공지사항을 찾을 수 없습니다"),

    // ========== Notification ==========
    NOTIFICATION_NOT_FOUND(404, "NT001", "알림을 찾을 수 없습니다"),
    NOTIFICATION_ACCESS_DENIED(403, "NT002", "알림 조회 권한이 없습니다"),

    // ========== Calendar ==========
    CALENDAR_EVENT_NOT_FOUND(404, "CA001", "일정을 찾을 수 없습니다"),
    CALENDAR_ACCESS_DENIED(403, "CA002", "일정 접근 권한이 없습니다"),

    // ========== Application ==========
    APPLICATION_NOT_FOUND(404, "AP001", "지원서를 찾을 수 없습니다"),
    APPLICATION_NOT_PENDING(400, "AP002", "처리 가능한 상태가 아닙니다"),
    APPLICATION_ALREADY_EXISTS(409, "AP003", "이미 지원한 유치원입니다"),
    PENDING_APPLICATION_EXISTS(400, "AP004", "이미 대기 중인 지원서가 있습니다"),
    APPLICATION_ACCESS_DENIED(403, "AP005", "지원서 접근 권한이 없습니다"),
    ALREADY_ASSIGNED_TO_KINDERGARTEN(400, "AP006", "이미 유치원에 배정된 회원입니다"),
    KINDERGARTEN_ACCESS_DENIED(403, "AP007", "유치원 접근 권한이 없습니다"),
    INVALID_MEMBER_ROLE(403, "AP008", "잘못된 회원 역할입니다"),
    APPLICATION_NOT_OFFERED(400, "AP009", "입학 offer 상태가 아닙니다"),
    APPLICATION_OFFER_EXPIRED(400, "AP010", "입학 offer가 만료되었습니다"),
    APPLICATION_INVALID_STATE(400, "AP011", "지원서 상태 전이가 올바르지 않습니다");

    /**
     * HTTP 상태 코드
     */
    private final int status;

    /**
     * 비즈니스 에러 코드
     */
    private final String code;

    /**
     * 에러 메시지
     */
    private final String message;
}
