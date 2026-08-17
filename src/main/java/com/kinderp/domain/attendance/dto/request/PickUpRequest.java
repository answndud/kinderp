package com.kinderp.domain.attendance.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 하원 기록 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class PickUpRequest {

    /**
     * 하원 시간
     */
    private LocalTime pickUpTime;
}
