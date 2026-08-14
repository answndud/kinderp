package com.erp.domain.calendar.entity;

import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.member.entity.Member;
import com.erp.global.common.BaseEntity;
import com.erp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 유치원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kindergarten_id")
    private Kindergarten kindergarten;

    /**
     * 반
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    /**
     * 작성자/소유자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Member creator;

    /**
     * 제목
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 설명
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 시작 일시
     */
    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDateTime;

    /**
     * 종료 일시
     */
    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDateTime;

    /**
     * 일정 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CalendarEventType eventType;

    /**
     * 범위 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private CalendarScopeType scopeType;

    /**
     * 종일 일정 여부
     */
    @Column(name = "is_all_day", nullable = false)
    private Boolean isAllDay = false;

    /**
     * 장소
     */
    @Column(name = "location", length = 200)
    private String location;

    /**
     * 반복 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false, length = 20)
    private RepeatType repeatType = RepeatType.NONE;

    /**
     * 반복 종료일
     */
    @Column(name = "repeat_end_date")
    private LocalDate repeatEndDate;

    /**
     * 삭제일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static CalendarEvent create(
            Kindergarten kindergarten,
            Classroom classroom,
            Member creator,
            String title,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            CalendarEventType eventType,
            CalendarScopeType scopeType,
            boolean isAllDay,
            String location,
            RepeatType repeatType,
            LocalDate repeatEndDate
    ) {
        CalendarEvent event = new CalendarEvent();
        event.kindergarten = kindergarten;
        event.classroom = classroom;
        event.creator = creator;
        event.title = title;
        event.description = description;
        event.startDateTime = startDateTime;
        event.endDateTime = endDateTime;
        event.eventType = eventType;
        event.scopeType = scopeType;
        event.isAllDay = isAllDay;
        event.location = location;
        event.repeatType = repeatType == null ? RepeatType.NONE : repeatType;
        event.repeatEndDate = repeatEndDate;
        return event;
    }

    public void update(
            Kindergarten kindergarten,
            Classroom classroom,
            String title,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            CalendarEventType eventType,
            CalendarScopeType scopeType,
            boolean isAllDay,
            String location,
            RepeatType repeatType,
            LocalDate repeatEndDate
    ) {
        this.kindergarten = kindergarten;
        this.classroom = classroom;
        this.title = title;
        this.description = description;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.eventType = eventType;
        this.scopeType = scopeType;
        this.isAllDay = isAllDay;
        this.location = location;
        this.repeatType = repeatType == null ? RepeatType.NONE : repeatType;
        this.repeatEndDate = repeatEndDate;
    }

    public void softDelete() {
        this.deletedAt = ProductTime.nowDateTime();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
