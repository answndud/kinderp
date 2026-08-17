package com.kinderp.domain.attendance.dto.request;

import com.kinderp.domain.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceChangeRequestCreateRequest(
        @NotNull(message = "원생 ID는 필수입니다")
        Long kidId,

        @NotNull(message = "날짜는 필수입니다")
        LocalDate date,

        @NotNull(message = "출결 상태는 필수입니다")
        AttendanceStatus status,

        LocalTime dropOffTime,

        LocalTime pickUpTime,

        @Size(max = 255, message = "메모는 255자 이하여야 합니다")
        String note
) {
}
