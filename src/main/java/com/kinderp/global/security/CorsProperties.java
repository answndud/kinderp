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
@ConfigurationProperties(prefix = "app.security.cors")
public class CorsProperties {

    /**
     * Credentialed CORS에서 허용할 origin 목록.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:8080"));

    public List<String> resolveAllowedOrigins() {
        List<String> origins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();
        if (origins.isEmpty()) {
            return List.of("http://localhost:8080");
        }
        return origins;
    }
}
