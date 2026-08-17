package com.kinderp.global.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.client-ip")
public class ClientIpProperties {

    /**
     * 전달 헤더를 신뢰할 프록시 IP 또는 CIDR 목록.
     * loopback은 기본적으로 신뢰한다.
     */
    private List<String> trustedProxies = new ArrayList<>();
}
