package com.kinderp.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seed")
public class SeedProperties {

    /**
     * 로컬/데모 시드 데이터 적재 여부.
     */
    private boolean enabled = false;

    /**
     * 시드 계정/비밀번호를 로그에 출력할지 여부.
     */
    private boolean logCredentials = false;
}
