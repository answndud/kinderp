package com.kinderp.domain.attendance.repository;

import com.kinderp.domain.attendance.entity.AttendanceChangeRequest;
import com.kinderp.domain.attendance.entity.AttendanceChangeRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceChangeRequestRepository extends JpaRepository<AttendanceChangeRequest, Long> {

    boolean existsByKidIdAndDateAndStatus(Long kidId, LocalDate date, AttendanceChangeRequestStatus status);

    Optional<AttendanceChangeRequest> findByRequesterIdAndIdempotencyKey(Long requesterId, String idempotencyKey);

    List<AttendanceChangeRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM AttendanceChangeRequest request WHERE request.id = :id")
    Optional<AttendanceChangeRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT request
            FROM AttendanceChangeRequest request
            WHERE request.kindergartenId = :kindergartenId
              AND request.status = :status
              AND (:classroomId IS NULL OR request.classroomId = :classroomId)
              AND (:date IS NULL OR request.date = :date)
            ORDER BY request.createdAt ASC
            """)
    List<AttendanceChangeRequest> findPendingRequests(@Param("kindergartenId") Long kindergartenId,
                                                      @Param("status") AttendanceChangeRequestStatus status,
                                                      @Param("classroomId") Long classroomId,
                                                      @Param("date") LocalDate date);
}
