package com.kinderp.domain.calendar.dto.response;

import com.kinderp.domain.calendar.entity.CalendarEvent;
import com.kinderp.domain.calendar.entity.CalendarEventType;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CalendarEventResponse(
        Long id,
        String title,
        String description,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        CalendarEventType eventType,
        CalendarScopeType scopeType,
        boolean isAllDay,
        String location,
        RepeatType repeatType,
        LocalDate repeatEndDate,
        Long kindergartenId,
        String kindergartenName,
        Long classroomId,
        String classroomName,
        Long creatorId,
        String creatorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CalendarEventResponse from(CalendarEvent event) {
        return from(event, event.getStartDateTime(), event.getEndDateTime());
    }

    public static CalendarEventResponse from(
            CalendarEvent event,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        Long kindergartenId = null;
        String kindergartenName = null;
        if (event.getKindergarten() != null) {
            kindergartenId = event.getKindergarten().getId();
            kindergartenName = event.getKindergarten().getName();
        }

        Long classroomId = null;
        String classroomName = null;
        if (event.getClassroom() != null) {
            classroomId = event.getClassroom().getId();
            classroomName = event.getClassroom().getName();
        }

        Long creatorId = null;
        String creatorName = null;
        if (event.getCreator() != null) {
            creatorId = event.getCreator().getId();
            creatorName = event.getCreator().getName();
        }

        return new CalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                startDateTime,
                endDateTime,
                event.getEventType(),
                event.getScopeType(),
                Boolean.TRUE.equals(event.getIsAllDay()),
                event.getLocation(),
                event.getRepeatType(),
                event.getRepeatEndDate(),
                kindergartenId,
                kindergartenName,
                classroomId,
                classroomName,
                creatorId,
                creatorName,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
