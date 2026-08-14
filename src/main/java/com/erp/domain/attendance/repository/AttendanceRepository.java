package com.erp.domain.attendance.repository;

import com.erp.domain.attendance.entity.Attendance;
import com.erp.domain.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 출석 리포지토리
 */
@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    interface DailyPresentCountProjection {
        LocalDate getDate();

        long getPresentCount();
    }

    /**
     * 원생별 날짜 출석 조회
     */
    @Query("SELECT a FROM Attendance a WHERE a.kid.id = :kidId AND a.date = :date")
    Optional<Attendance> findByKidIdAndDate(@Param("kidId") Long kidId, @Param("date") LocalDate date);

    /**
     * 반별 날짜 출석 목록 조회
     */
    @Query("SELECT a FROM Attendance a WHERE a.kid.classroom.id = :classroomId AND a.date = :date ORDER BY a.kid.name")
    List<Attendance> findByClassroomIdAndDate(@Param("classroomId") Long classroomId, @Param("date") LocalDate date);

    /**
     * 반별 월간 출석 목록 조회
     */
    @Query("SELECT a FROM Attendance a WHERE a.kid.classroom.id = :classroomId AND a.date >= :startDate AND a.date <= :endDate")
    List<Attendance> findByClassroomIdAndDateBetween(@Param("classroomId") Long classroomId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    /**
     * 원생별 월간 출석 목록 조회
     */
    @Query("SELECT a FROM Attendance a WHERE a.kid.id = :kidId AND a.date >= :startDate AND a.date <= :endDate ORDER BY a.date DESC")
    List<Attendance> findByKidIdAndDateBetween(@Param("kidId") Long kidId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * 원생별 특정 상태 출석 일수 조회
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.kid.id = :kidId AND a.date >= :startDate AND a.date <= :endDate AND a.status = :status")
    long countByKidIdAndDateBetweenAndStatus(@Param("kidId") Long kidId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate,
                                              @Param("status") AttendanceStatus status);

    /**
     * 원생별 월간 출석 통계 (출석일수)
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.kid.id = :kidId AND a.date >= :startDate AND a.date <= :endDate AND (a.status = 'PRESENT' OR a.status = 'LATE')")
    long countPresentDaysByKidIdAndDateBetween(@Param("kidId") Long kidId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * 원생별 월간 결석일수 조회
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.kid.id = :kidId AND a.date >= :startDate AND a.date <= :endDate AND (a.status = 'ABSENT' OR a.status = 'SICK_LEAVE')")
    long countAbsentDaysByKidIdAndDateBetween(@Param("kidId") Long kidId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    /**
     * 특정 날짜 반별 출석 현황 (원생 ID만)
     */
    @Query("SELECT a.kid.id FROM Attendance a WHERE a.kid.classroom.id = :classroomId AND a.date = :date")
    List<Long> findKidIdsByClassroomIdAndDate(@Param("classroomId") Long classroomId, @Param("date") LocalDate date);

    /**
     * 유치원별 특정 날짜 출석 수
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.kid.classroom.kindergarten.id = :kindergartenId AND a.date = :date")
    long countByKidClassroomKindergartenIdAndDate(@Param("kindergartenId") Long kindergartenId, @Param("date") LocalDate date);

    /**
     * 유치원별 기간 내 특정 상태 출석 수
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.kid.classroom.kindergarten.id = :kindergartenId AND a.date >= :startDate AND a.date <= :endDate AND a.status = :status")
    long countByKidClassroomKindergartenIdAndDateBetweenAndStatus(@Param("kindergartenId") Long kindergartenId,
                                                              @Param("startDate") LocalDate startDate,
                                                              @Param("endDate") LocalDate endDate,
                                                              @Param("status") AttendanceStatus status);

    @Query("SELECT a.date AS date, COUNT(a) AS presentCount " +
            "FROM Attendance a " +
            "WHERE a.kid.id IN :kidIds " +
            "AND a.date >= :startDate AND a.date <= :endDate " +
            "AND a.status IN ('PRESENT', 'LATE') " +
            "GROUP BY a.date")
    List<DailyPresentCountProjection> findPresentCountsByKidIdsAndDateBetween(
            @Param("kidIds") List<Long> kidIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.kid.classroom.kindergarten.id = :kindergartenId " +
            "AND a.date >= :startDate AND a.date <= :endDate " +
            "AND a.status IN ('PRESENT', 'LATE')")
    long countPresentOrLateByKindergartenAndDateBetween(@Param("kindergartenId") Long kindergartenId,
                                                        @Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);

    @Query("SELECT a.date AS date, COUNT(a) AS presentCount " +
            "FROM Attendance a " +
            "WHERE a.kid.classroom.kindergarten.id = :kindergartenId " +
            "AND a.date >= :startDate AND a.date <= :endDate " +
            "AND a.status IN ('PRESENT', 'LATE') " +
            "GROUP BY a.date")
    List<DailyPresentCountProjection> findPresentOrLateCountsByKindergartenAndDateBetween(
            @Param("kindergartenId") Long kindergartenId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
