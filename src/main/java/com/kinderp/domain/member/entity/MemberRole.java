package com.kinderp.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 역할 enum
 */
@Getter
@RequiredArgsConstructor
public enum MemberRole {

    /**
     * 원장 - 전체 관리, 승인 권한
     */
    PRINCIPAL("ROLE_PRINCIPAL", "원장"),

    /**
     * 교사 - 반 관리, 출석, 알림장 작성
     */
    TEACHER("ROLE_TEACHER", "교사"),

    /**
     * 학부모 - 알림장, 출석 조회
     */
    PARENT("ROLE_PARENT", "학부모");

    /**
     * Spring Security 역할 코드 (접두사 ROLE_ 필수)
     */
    private final String key;

    /**
     * 역할 설명
     */
    private final String title;
}
