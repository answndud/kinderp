package com.kinderp.domain.calendar.service;

import com.kinderp.domain.calendar.dto.request.CalendarEventRequest;
import com.kinderp.domain.calendar.dto.response.CalendarEventResponse;
import com.kinderp.domain.calendar.entity.CalendarEvent;
import com.kinderp.domain.calendar.entity.CalendarEventType;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;
import com.kinderp.domain.calendar.repository.CalendarEventRepository;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.classroom.service.ClassroomService;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.service.KidService;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final MemberService memberService;
    private final ClassroomService classroomService;
    private final ClassroomRepository classroomRepository;
    private final KidService kidService;
    private final RecurrenceExpander recurrenceExpander;

    private static final long MAX_QUERY_RANGE_DAYS = 366;

    @Transactional
    public Long createEvent(CalendarEventRequest request, Long memberId) {
        Member member = memberService.getMemberByIdWithKindergarten(memberId);
        validateDateRange(request.getStartDateTime(), request.getEndDateTime());
        validateRepeatRule(request);

        ScopeContext scopeContext = resolveScopeContext(member, request);

        CalendarEvent event = CalendarEvent.create(
                scopeContext.kindergarten,
                scopeContext.classroom,
                member,
                request.getTitle(),
                request.getDescription(),
                request.getStartDateTime(),
                request.getEndDateTime(),
                request.getEventType(),
                scopeContext.scopeType,
                Boolean.TRUE.equals(request.getIsAllDay()),
                request.getLocation(),
                request.getRepeatType(),
                request.getRepeatEndDate()
        );

        CalendarEvent saved = calendarEventRepository.save(event);
        return saved.getId();
    }

    public CalendarEventResponse getEvent(Long eventId, Long memberId) {
        CalendarEvent event = getEventEntity(eventId);
        Member member = memberService.getMemberByIdWithKindergarten(memberId);
        validateViewPermission(member, event);
        return CalendarEventResponse.from(event);
    }

    public List<CalendarEventResponse> getEvents(
            Long memberId,
            LocalDate startDate,
            LocalDate endDate,
            CalendarScopeType scopeType,
            Long classroomId
    ) {
        Member member = memberService.getMemberByIdWithKindergarten(memberId);
        validateDateRange(startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<CalendarEvent> events = new ArrayList<>();

        if (scopeType != null) {
            events.addAll(loadEventsByScope(member, scopeType, classroomId, startDateTime, endDateTime));
        } else {
            if (member.getRole() == MemberRole.PRINCIPAL) {
                events.addAll(loadEventsByScope(member, CalendarScopeType.KINDERGARTEN, classroomId, startDateTime, endDateTime));
                events.addAll(loadEventsByScope(member, CalendarScopeType.PERSONAL, classroomId, startDateTime, endDateTime));
            } else if (member.getRole() == MemberRole.TEACHER) {
                events.addAll(loadEventsByScope(member, CalendarScopeType.KINDERGARTEN, classroomId, startDateTime, endDateTime));
                events.addAll(loadEventsByScope(member, CalendarScopeType.CLASSROOM, classroomId, startDateTime, endDateTime));
                events.addAll(loadEventsByScope(member, CalendarScopeType.PERSONAL, classroomId, startDateTime, endDateTime));
            } else if (member.getRole() == MemberRole.PARENT) {
                events.addAll(loadEventsByScope(member, CalendarScopeType.KINDERGARTEN, classroomId, startDateTime, endDateTime));
                events.addAll(loadEventsByScope(member, CalendarScopeType.CLASSROOM, classroomId, startDateTime, endDateTime));
                events.addAll(loadEventsByScope(member, CalendarScopeType.PERSONAL, classroomId, startDateTime, endDateTime));
            }
        }

        return recurrenceExpander.expand(events, startDateTime, endDateTime);
    }

    @Transactional
    public void updateEvent(Long eventId, CalendarEventRequest request, Long memberId) {
        CalendarEvent event = getEventEntity(eventId);
        Member member = memberService.getMemberByIdWithKindergarten(memberId);
        validateDateRange(request.getStartDateTime(), request.getEndDateTime());
        validateRepeatRule(request);
        validateManagePermission(member, event);

        ScopeContext scopeContext = resolveScopeContext(member, request);

        event.update(
                scopeContext.kindergarten,
                scopeContext.classroom,
                request.getTitle(),
                request.getDescription(),
                request.getStartDateTime(),
                request.getEndDateTime(),
                request.getEventType(),
                scopeContext.scopeType,
                Boolean.TRUE.equals(request.getIsAllDay()),
                request.getLocation(),
                request.getRepeatType(),
                request.getRepeatEndDate()
        );
    }

    @Transactional
    public void deleteEvent(Long eventId, Long memberId) {
        CalendarEvent event = getEventEntity(eventId);
        Member member = memberService.getMemberByIdWithKindergarten(memberId);
        validateManagePermission(member, event);
        event.softDelete();
    }

    private CalendarEvent getEventEntity(Long eventId) {
        return calendarEventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
    }

    private void validateDateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (startDateTime == null || endDateTime == null || endDateTime.isBefore(startDateTime)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (rangeDays > MAX_QUERY_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "캘린더 조회 기간은 최대 366일입니다");
        }
    }

    private void validateRepeatRule(CalendarEventRequest request) {
        RepeatType repeatType = request.getRepeatType() == null ? RepeatType.NONE : request.getRepeatType();
        LocalDate repeatEndDate = request.getRepeatEndDate();

        if (repeatType == RepeatType.NONE) {
            if (repeatEndDate != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return;
        }

        if (repeatEndDate == null || request.getStartDateTime() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (repeatEndDate.isBefore(request.getStartDateTime().toLocalDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ScopeContext resolveScopeContext(Member member, CalendarEventRequest request) {
        CalendarScopeType scopeType = request.getScopeType();
        CalendarEventType eventType = request.getEventType();

        if (scopeType == null || eventType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Kindergarten kindergarten = null;
        Classroom classroom = null;

        if (scopeType == CalendarScopeType.KINDERGARTEN) {
            if (member.getRole() != MemberRole.PRINCIPAL) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            if (member.getKindergarten() == null) {
                throw new BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND);
            }
            kindergarten = member.getKindergarten();
        } else if (scopeType == CalendarScopeType.CLASSROOM) {
            if (member.getRole() != MemberRole.TEACHER) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            if (request.getClassroomId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            classroom = classroomService.getClassroom(request.getClassroomId());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getId().equals(member.getId())) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            kindergarten = classroom.getKindergarten();
        } else if (scopeType == CalendarScopeType.PERSONAL) {
            kindergarten = member.getKindergarten();
        }

        return new ScopeContext(scopeType, kindergarten, classroom);
    }

    private void validateViewPermission(Member member, CalendarEvent event) {
        CalendarScopeType scopeType = event.getScopeType();

        if (scopeType == CalendarScopeType.PERSONAL) {
            if (!event.getCreator().getId().equals(member.getId())) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            return;
        }

        if (scopeType == CalendarScopeType.KINDERGARTEN) {
            if (member.getKindergarten() == null) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            if (!isSameKindergarten(member, event.getKindergarten())) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            return;
        }

        if (scopeType == CalendarScopeType.CLASSROOM) {
            if (member.getRole() == MemberRole.TEACHER) {
                if (!isTeacherOfClassroom(member, event.getClassroom())) {
                    throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
                }
                return;
            }

            if (member.getRole() == MemberRole.PARENT) {
                if (!isParentOfClassroom(member.getId(), event.getClassroom())) {
                    throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
                }
                return;
            }

            throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
        }
    }

    private void validateManagePermission(Member member, CalendarEvent event) {
        CalendarScopeType scopeType = event.getScopeType();

        if (scopeType == CalendarScopeType.PERSONAL) {
            if (!event.getCreator().getId().equals(member.getId())) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            return;
        }

        if (scopeType == CalendarScopeType.KINDERGARTEN) {
            if (member.getRole() != MemberRole.PRINCIPAL) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            if (!isSameKindergarten(member, event.getKindergarten())) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            return;
        }

        if (scopeType == CalendarScopeType.CLASSROOM) {
            if (member.getRole() != MemberRole.TEACHER) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
            if (!isTeacherOfClassroom(member, event.getClassroom())) {
                throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
            }
        }
    }

    private boolean isSameKindergarten(Member member, Kindergarten kindergarten) {
        return member.getKindergarten() != null && kindergarten != null
                && member.getKindergarten().getId().equals(kindergarten.getId());
    }

    private boolean isTeacherOfClassroom(Member member, Classroom classroom) {
        return classroom != null && classroom.getTeacher() != null
                && classroom.getTeacher().getId().equals(member.getId());
    }

    private boolean isParentOfClassroom(Long parentId, Classroom classroom) {
        if (classroom == null) {
            return false;
        }
        List<Kid> kids = kidService.getKidsByParent(parentId);
        return kids.stream()
                .anyMatch(kid -> kid.getClassroom() != null && classroom.getId().equals(kid.getClassroom().getId()));
    }

    private List<CalendarEvent> loadEventsByScope(
            Member member,
            CalendarScopeType scopeType,
            Long classroomId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        if (scopeType == CalendarScopeType.KINDERGARTEN) {
            if (member.getKindergarten() == null) {
                return List.of();
            }
            return calendarEventRepository.findKindergartenEvents(
                    member.getKindergarten().getId(),
                    CalendarScopeType.KINDERGARTEN,
                    RepeatType.NONE,
                    startDateTime,
                    endDateTime,
                    startDateTime.toLocalDate()
            );
        }

        if (scopeType == CalendarScopeType.CLASSROOM) {
            List<Long> classroomIds = resolveAccessibleClassroomIds(member);
            if (classroomId != null) {
                if (!classroomIds.contains(classroomId)) {
                    throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
                }
                classroomIds = List.of(classroomId);
            }
            if (classroomIds.isEmpty()) {
                return List.of();
            }
            return calendarEventRepository.findClassroomEvents(
                    classroomIds,
                    CalendarScopeType.CLASSROOM,
                    RepeatType.NONE,
                    startDateTime,
                    endDateTime,
                    startDateTime.toLocalDate()
            );
        }

        if (scopeType == CalendarScopeType.PERSONAL) {
            return calendarEventRepository.findPersonalEvents(
                    member.getId(),
                    CalendarScopeType.PERSONAL,
                    RepeatType.NONE,
                    startDateTime,
                    endDateTime,
                    startDateTime.toLocalDate()
            );
        }

        return List.of();
    }

    private List<Long> resolveAccessibleClassroomIds(Member member) {
        if (member.getRole() == MemberRole.TEACHER) {
            Optional<Classroom> classroom = classroomRepository.findByTeacherIdAndDeletedAtIsNull(member.getId());
            return classroom.map(value -> List.of(value.getId())).orElseGet(List::of);
        }

        if (member.getRole() == MemberRole.PARENT) {
            List<Kid> kids = kidService.getKidsByParent(member.getId());
            return kids.stream()
                    .map(Kid::getClassroom)
                    .filter(classroom -> classroom != null)
                    .map(Classroom::getId)
                    .distinct()
                    .toList();
        }

        return List.of();
    }

    private record ScopeContext(CalendarScopeType scopeType, Kindergarten kindergarten, Classroom classroom) {
    }
}
