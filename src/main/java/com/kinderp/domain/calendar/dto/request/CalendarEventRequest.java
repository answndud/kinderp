package com.kinderp.domain.calendar.dto.request;

import com.kinderp.domain.calendar.entity.CalendarEventType;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CalendarEventRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다")
    private String title;

    private String description;

    @NotNull(message = "시작 일시는 필수입니다")
    private LocalDateTime startDateTime;

    @NotNull(message = "종료 일시는 필수입니다")
    private LocalDateTime endDateTime;

    @NotNull(message = "일정 유형은 필수입니다")
    private CalendarEventType eventType;

    @NotNull(message = "범위 유형은 필수입니다")
    private CalendarScopeType scopeType;

    private Boolean isAllDay = false;

    private String location;

    private RepeatType repeatType = RepeatType.NONE;

    private LocalDate repeatEndDate;

    private Long classroomId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public CalendarEventType getEventType() {
        return eventType;
    }

    public void setEventType(CalendarEventType eventType) {
        this.eventType = eventType;
    }

    public CalendarScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(CalendarScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Boolean getIsAllDay() {
        return isAllDay;
    }

    public void setIsAllDay(Boolean isAllDay) {
        this.isAllDay = isAllDay;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public RepeatType getRepeatType() {
        return repeatType;
    }

    public void setRepeatType(RepeatType repeatType) {
        this.repeatType = repeatType;
    }

    public LocalDate getRepeatEndDate() {
        return repeatEndDate;
    }

    public void setRepeatEndDate(LocalDate repeatEndDate) {
        this.repeatEndDate = repeatEndDate;
    }

    public Long getClassroomId() {
        return classroomId;
    }

    public void setClassroomId(Long classroomId) {
        this.classroomId = classroomId;
    }
}
