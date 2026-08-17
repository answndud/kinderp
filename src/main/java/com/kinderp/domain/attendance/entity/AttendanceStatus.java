package com.kinderp.domain.attendance.entity;

import lombok.Getter;

/**
 * 출석 상태 enum
 */
@Getter
public enum AttendanceStatus {
    PRESENT("출석"),
    ABSENT("결석"),
    LATE("지각"),
    EARLY_LEAVE("조퇴"),
    SICK_LEAVE("병결");

    private final String description;

    AttendanceStatus(String description) {
        this.description = description;
    }
}
