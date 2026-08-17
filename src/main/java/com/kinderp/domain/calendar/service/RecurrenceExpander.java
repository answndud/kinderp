package com.kinderp.domain.calendar.service;

import com.kinderp.domain.calendar.dto.response.CalendarEventResponse;
import com.kinderp.domain.calendar.entity.CalendarEvent;
import com.kinderp.domain.calendar.entity.RepeatType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecurrenceExpander {

    public List<CalendarEventResponse> expand(
            List<CalendarEvent> events,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        return events.stream()
                .flatMap(event -> expandEvent(event, rangeStart, rangeEnd).stream())
                .sorted(Comparator.comparing(CalendarEventResponse::startDateTime)
                        .thenComparing(CalendarEventResponse::id))
                .toList();
    }

    List<CalendarEventResponse> expandEvent(
            CalendarEvent event,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        RepeatType repeatType = event.getRepeatType() == null ? RepeatType.NONE : event.getRepeatType();
        if (repeatType == RepeatType.NONE || event.getRepeatEndDate() == null) {
            if (!overlaps(event.getStartDateTime(), event.getEndDateTime(), rangeStart, rangeEnd)) {
                return List.of();
            }
            return List.of(CalendarEventResponse.from(event));
        }

        Duration duration = Duration.between(event.getStartDateTime(), event.getEndDateTime());
        LocalDateTime occurrenceStart = event.getStartDateTime();
        List<CalendarEventResponse> responses = new ArrayList<>();

        while (!occurrenceStart.toLocalDate().isAfter(event.getRepeatEndDate())) {
            LocalDateTime occurrenceEnd = occurrenceStart.plus(duration);
            if (overlaps(occurrenceStart, occurrenceEnd, rangeStart, rangeEnd)) {
                responses.add(CalendarEventResponse.from(event, occurrenceStart, occurrenceEnd));
            }
            if (occurrenceStart.isAfter(rangeEnd)) {
                break;
            }
            occurrenceStart = advanceOccurrence(occurrenceStart, repeatType);
        }

        return responses;
    }

    private boolean overlaps(
            LocalDateTime occurrenceStart,
            LocalDateTime occurrenceEnd,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        return !occurrenceStart.isAfter(rangeEnd) && !occurrenceEnd.isBefore(rangeStart);
    }

    private LocalDateTime advanceOccurrence(LocalDateTime occurrenceStart, RepeatType repeatType) {
        return switch (repeatType) {
            case DAILY -> occurrenceStart.plusDays(1);
            case WEEKLY -> occurrenceStart.plusWeeks(1);
            case MONTHLY -> occurrenceStart.plusMonths(1);
            case NONE -> occurrenceStart;
        };
    }
}
