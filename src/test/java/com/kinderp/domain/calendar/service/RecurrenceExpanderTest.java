package com.kinderp.domain.calendar.service;

import com.kinderp.domain.calendar.dto.response.CalendarEventResponse;
import com.kinderp.domain.calendar.entity.CalendarEvent;
import com.kinderp.domain.calendar.entity.CalendarEventType;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class RecurrenceExpanderTest {

    private final RecurrenceExpander recurrenceExpander = new RecurrenceExpander();

    @Test
    @DisplayName("반복 일정 occurrence를 조회 범위 안에서만 확장한다")
    void expandWeeklyRecurringEventWithinRange() {
        CalendarEvent event = CalendarEvent.create(
                null,
                null,
                null,
                "주간 상담",
                "매주 상담",
                LocalDateTime.of(2026, 3, 3, 10, 0),
                LocalDateTime.of(2026, 3, 3, 11, 0),
                CalendarEventType.MEETING,
                CalendarScopeType.PERSONAL,
                false,
                null,
                RepeatType.WEEKLY,
                LocalDate.of(2026, 3, 31)
        );

        List<CalendarEventResponse> responses = recurrenceExpander.expand(
                List.of(event),
                LocalDateTime.of(2026, 3, 10, 0, 0),
                LocalDateTime.of(2026, 3, 24, 23, 59)
        );

        assertThat(responses)
                .extracting(CalendarEventResponse::startDateTime)
                .containsExactly(
                        LocalDateTime.of(2026, 3, 10, 10, 0),
                        LocalDateTime.of(2026, 3, 17, 10, 0),
                        LocalDateTime.of(2026, 3, 24, 10, 0)
                );
    }
}
