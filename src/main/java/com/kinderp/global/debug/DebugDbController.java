package com.kinderp.global.debug;

import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Profile("local")
@ConditionalOnProperty(prefix = "app.security.debug-db", name = "enabled", havingValue = "true")
@RestController
@RequiredArgsConstructor
public class DebugDbController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/debug/db")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> db(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("memberId", userDetails.getMemberId());
        info.put("role", userDetails.getRole().name());

        info.put("counts", Map.of(
                "member", jdbcTemplate.queryForObject("select count(*) from member", Long.class),
                "kindergarten", jdbcTemplate.queryForObject("select count(*) from kindergarten", Long.class),
                "classroom", jdbcTemplate.queryForObject("select count(*) from classroom", Long.class),
                "kid", jdbcTemplate.queryForObject("select count(*) from kid", Long.class)
        ));

        return ResponseEntity.ok(ApiResponse.success(info));
    }
}
