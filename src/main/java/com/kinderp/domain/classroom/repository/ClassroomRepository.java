package com.kinderp.domain.classroom.repository;

import com.kinderp.domain.classroom.entity.Classroom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 반 리포지토리
 */
@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    /**
     * 유치원별 반 목록 조회 (삭제되지 않은 반만)
     */
    @Query("SELECT c FROM Classroom c " +
           "JOIN FETCH c.kindergarten k " +
           "LEFT JOIN FETCH c.teacher t " +
           "WHERE k.id = :kindergartenId AND c.deletedAt IS NULL " +
           "ORDER BY c.name")
    List<Classroom> findByKindergartenIdAndDeletedAtIsNull(@Param("kindergartenId") Long kindergartenId);

    /**
     * ID로 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT c FROM Classroom c " +
           "JOIN FETCH c.kindergarten k " +
           "LEFT JOIN FETCH c.teacher t " +
           "WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Classroom> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Classroom c " +
           "JOIN FETCH c.kindergarten k " +
           "LEFT JOIN FETCH c.teacher t " +
           "WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Classroom> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    /**
     * 유치원별 반 개수 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT COUNT(c) FROM Classroom c WHERE c.kindergarten.id = :kindergartenId AND c.deletedAt IS NULL")
    long countByKindergartenIdAndDeletedAtIsNull(@Param("kindergartenId") Long kindergartenId);

    /**
     * 교사별 반 조회
     */
    @Query("SELECT c FROM Classroom c " +
           "JOIN FETCH c.kindergarten k " +
           "LEFT JOIN FETCH c.teacher t " +
           "WHERE t.id = :teacherId AND c.deletedAt IS NULL")
    Optional<Classroom> findByTeacherIdAndDeletedAtIsNull(@Param("teacherId") Long teacherId);
}
