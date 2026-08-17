package com.kinderp.domain.authaudit.service;

import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthAuditMetricsService {

    private static final String METER_NAME = "erp.auth.events";

    private final MeterRegistry meterRegistry;

    public void record(AuthAuditEventType eventType, AuthAuditResult result, MemberAuthProvider provider) {
        meterRegistry.counter(
                METER_NAME,
                "event_type", normalize(eventType),
                "result", normalize(result),
                "provider", normalize(provider)
        ).increment();
    }

    private String normalize(Enum<?> value) {
        if (value == null) {
            return "none";
        }
        return value.name().toLowerCase();
    }
}
