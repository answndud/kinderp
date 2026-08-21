package com.kinderp.global.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.validation.annotation.Validated;

/** HTTP 요청 자원 제한 정책. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "server.request-limits")
public class RequestLimitProperties {

    @DataSizeUnit(DataUnit.BYTES)
    private DataSize maxRequestSize = DataSize.ofMegabytes(1);

    @Positive
    private int maxHeaderCount = 100;

    @Positive
    private int maxParameterCount = 100;
}
