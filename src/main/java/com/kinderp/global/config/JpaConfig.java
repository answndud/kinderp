package com.kinderp.global.config;

import com.kinderp.global.common.ProductTime;
import java.util.Optional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 설정
 * JPA Auditing을 활성화합니다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "productDateTimeProvider")
public class JpaConfig {

    @Bean
    DateTimeProvider productDateTimeProvider() {
        return () -> Optional.of(ProductTime.nowDateTime());
    }
}
