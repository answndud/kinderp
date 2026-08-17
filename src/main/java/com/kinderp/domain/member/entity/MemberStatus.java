package com.kinderp.domain.member.entity;

import lombok.Getter;

/**
 * 회원 상태 enum
 */
@Getter
public enum MemberStatus {

    /**
     * 활성 - 로그인 가능
     */
    ACTIVE("활성"),

    /**
     * 비활성 - 탈퇴 또는 정지
     */
    INACTIVE("비활성"),

    /**
     * 승인 대기 - 유치원 승인 필요
     */
    PENDING("승인 대기");

    private final String description;

    MemberStatus(String description) {
        this.description = description;
    }
}
