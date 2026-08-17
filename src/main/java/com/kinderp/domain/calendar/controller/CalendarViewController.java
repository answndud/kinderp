package com.kinderp.domain.calendar.controller;

import com.kinderp.domain.calendar.dto.response.CalendarEventResponse;
import com.kinderp.domain.calendar.entity.CalendarEventType;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;
import com.kinderp.domain.calendar.service.CalendarEventService;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CalendarViewController {

    private final CalendarEventService calendarEventService;

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String calendarPage(Model model) {
        model.addAttribute("eventTypes", CalendarEventType.values());
        model.addAttribute("scopeTypes", CalendarScopeType.values());
        model.addAttribute("repeatTypes", RepeatType.values());
        return "calendar/calendar";
    }

    @GetMapping("/calendar/list")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String calendarList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) CalendarScopeType scopeType,
            @RequestParam(required = false) Long classroomId,
            Model model
    ) {
        List<CalendarEventResponse> events = calendarEventService.getEvents(
                userDetails.getMemberId(),
                startDate,
                endDate,
                scopeType,
                classroomId
        );
        model.addAttribute("events", events);
        return "calendar/fragments/list :: list";
    }
}
