package com.kinderp.domain.attendance.dto.request;

import com.kinderp.domain.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class BulkAttendanceRequest {

    @NotNull(message = "반 ID는 필수입니다")
    private Long classroomId;

    @NotNull(message = "날짜는 필수입니다")
    private LocalDate date;

    @NotNull(message = "출석 상태는 필수입니다")
    private AttendanceStatus status;

    private LocalTime dropOffTime;

    private LocalTime pickUpTime;

    private String note;

    private List<Long> kidIds;
}
