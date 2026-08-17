package com.kinderp.domain.calendar.controller;

import com.kinderp.domain.calendar.dto.request.CalendarEventRequest;
import com.kinderp.domain.calendar.dto.response.CalendarEventResponse;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.service.CalendarEventService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.common.ProductTime;
import com.kinderp.global.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar/events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) CalendarScopeType scopeType,
            @RequestParam(required = false) Long classroomId
    ) {
        List<CalendarEventResponse> responses = calendarEventService.getEvents(
                userDetails.getMemberId(),
                startDate,
                endDate,
                scopeType,
                classroomId
        );

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> getEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CalendarEventResponse response = calendarEventService.getEvent(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getTodayEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        LocalDate today = ProductTime.today();
        List<CalendarEventResponse> responses = calendarEventService.getEvents(
                userDetails.getMemberId(),
                today,
                today,
                null,
                null
        );
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getUpcomingEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "7") int days
    ) {
        int safeDays = Math.min(Math.max(days, 1), 30);
        LocalDate startDate = ProductTime.today();
        LocalDate endDate = startDate.plusDays(safeDays);
        List<CalendarEventResponse> responses = calendarEventService.getEvents(
                userDetails.getMemberId(),
                startDate,
                endDate,
                null,
                null
        );
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> createEvent(
            @Valid @RequestBody CalendarEventRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long id = calendarEventService.createEvent(request, userDetails.getMemberId());
        CalendarEventResponse response = calendarEventService.getEvent(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response, "일정이 등록되었습니다"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody CalendarEventRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        calendarEventService.updateEvent(id, request, userDetails.getMemberId());
        CalendarEventResponse response = calendarEventService.getEvent(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(response, "일정이 수정되었습니다"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        calendarEventService.deleteEvent(id, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(null, "일정이 삭제되었습니다"));
    }
}
