package com.erp.global.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class ProductTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private ProductTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE_ID);
    }

    public static LocalTime now() {
        return LocalTime.now(ZONE_ID);
    }

    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now(ZONE_ID);
    }
}
