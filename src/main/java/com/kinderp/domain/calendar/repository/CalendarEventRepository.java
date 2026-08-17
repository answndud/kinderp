package com.kinderp.domain.calendar.repository;

import com.kinderp.domain.calendar.entity.CalendarEvent;
import com.kinderp.domain.calendar.entity.CalendarScopeType;
import com.kinderp.domain.calendar.entity.RepeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    boolean existsByKindergartenIdAndTitleAndDeletedAtIsNull(Long kindergartenId, String title);

    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.kindergarten k " +
           "LEFT JOIN FETCH e.classroom c " +
           "LEFT JOIN FETCH e.creator m " +
           "WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<CalendarEvent> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.kindergarten k " +
           "LEFT JOIN FETCH e.classroom c " +
           "LEFT JOIN FETCH e.creator m " +
           "WHERE e.scopeType = :scopeType AND k.id = :kindergartenId " +
           "AND e.deletedAt IS NULL " +
           "AND (" +
           "    (e.repeatType = :noneRepeatType AND e.startDateTime <= :endDateTime AND e.endDateTime >= :startDateTime) " +
           "    OR " +
           "    (e.repeatType <> :noneRepeatType AND e.startDateTime <= :endDateTime AND (" +
           "        (e.repeatEndDate IS NOT NULL AND e.repeatEndDate >= :startDate) " +
           "        OR (e.repeatEndDate IS NULL AND e.endDateTime >= :startDateTime)" +
           "    ))" +
           ") " +
           "ORDER BY e.startDateTime ASC")
    List<CalendarEvent> findKindergartenEvents(
            @Param("kindergartenId") Long kindergartenId,
            @Param("scopeType") CalendarScopeType scopeType,
            @Param("noneRepeatType") RepeatType noneRepeatType,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("startDate") LocalDate startDate
    );

    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.kindergarten k " +
           "LEFT JOIN FETCH e.classroom c " +
           "LEFT JOIN FETCH e.creator m " +
           "WHERE e.scopeType = :scopeType AND c.id IN :classroomIds " +
           "AND e.deletedAt IS NULL " +
           "AND (" +
           "    (e.repeatType = :noneRepeatType AND e.startDateTime <= :endDateTime AND e.endDateTime >= :startDateTime) " +
           "    OR " +
           "    (e.repeatType <> :noneRepeatType AND e.startDateTime <= :endDateTime AND (" +
           "        (e.repeatEndDate IS NOT NULL AND e.repeatEndDate >= :startDate) " +
           "        OR (e.repeatEndDate IS NULL AND e.endDateTime >= :startDateTime)" +
           "    ))" +
           ") " +
           "ORDER BY e.startDateTime ASC")
    List<CalendarEvent> findClassroomEvents(
            @Param("classroomIds") List<Long> classroomIds,
            @Param("scopeType") CalendarScopeType scopeType,
            @Param("noneRepeatType") RepeatType noneRepeatType,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("startDate") LocalDate startDate
    );

    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.kindergarten k " +
           "LEFT JOIN FETCH e.classroom c " +
           "LEFT JOIN FETCH e.creator m " +
           "WHERE e.scopeType = :scopeType AND m.id = :memberId " +
           "AND e.deletedAt IS NULL " +
           "AND (" +
           "    (e.repeatType = :noneRepeatType AND e.startDateTime <= :endDateTime AND e.endDateTime >= :startDateTime) " +
           "    OR " +
           "    (e.repeatType <> :noneRepeatType AND e.startDateTime <= :endDateTime AND (" +
           "        (e.repeatEndDate IS NOT NULL AND e.repeatEndDate >= :startDate) " +
           "        OR (e.repeatEndDate IS NULL AND e.endDateTime >= :startDateTime)" +
           "    ))" +
           ") " +
           "ORDER BY e.startDateTime ASC")
    List<CalendarEvent> findPersonalEvents(
            @Param("memberId") Long memberId,
            @Param("scopeType") CalendarScopeType scopeType,
            @Param("noneRepeatType") RepeatType noneRepeatType,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("startDate") LocalDate startDate
    );
}
