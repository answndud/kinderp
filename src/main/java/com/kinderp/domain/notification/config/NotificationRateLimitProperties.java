package com.kinderp.domain.notification.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 사용자 입력으로 생성되는 외부 알림 발송 요청의 rate limit 정책. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "notification.delivery.rate-limit")
public class NotificationRateLimitProperties {

    @NotNull
    private Duration window = Duration.ofMinutes(1);

    @Positive
    private long userLimit = 30;

    @Positive
    private long ipLimit = 100;
}
