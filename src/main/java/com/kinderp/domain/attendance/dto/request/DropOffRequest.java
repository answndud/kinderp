package com.kinderp.domain.attendance.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 등원 기록 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class DropOffRequest {

    /**
     * 등원 시간
     */
    private LocalTime dropOffTime;
}
