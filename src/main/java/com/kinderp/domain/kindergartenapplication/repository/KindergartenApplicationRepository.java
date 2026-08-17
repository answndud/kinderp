package com.kinderp.domain.kindergartenapplication.repository;

import com.kinderp.domain.kindergartenapplication.entity.ApplicationStatus;
import com.kinderp.domain.kindergartenapplication.entity.KindergartenApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KindergartenApplicationRepository extends JpaRepository<KindergartenApplication, Long> {

    /**
     * 교사의 지원서 목록 조회
     */
    List<KindergartenApplication> findByTeacherIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long teacherId);

    /**
     * 유치원별 대기 중인 지원서 목록
     */
    @Query("SELECT a FROM KindergartenApplication a WHERE a.kindergarten.id = :kindergartenId AND a.status = 'PENDING' AND a.deletedAt IS NULL ORDER BY a.createdAt ASC")
    List<KindergartenApplication> findPendingApplicationsByKindergartenId(@Param("kindergartenId") Long kindergartenId);

    /**
     * 교사가 해당 유치원에 대기 중인 지원서가 있는지 확인
     */
    @Query("SELECT a FROM KindergartenApplication a WHERE a.teacher.id = :teacherId AND a.kindergarten.id = :kindergartenId AND a.status = 'PENDING' AND a.deletedAt IS NULL")
    Optional<KindergartenApplication> findPendingApplicationByTeacherAndKindergarten(
            @Param("teacherId") Long teacherId,
            @Param("kindergartenId") Long kindergartenId
    );

    /**
     * 교사가 해당 유치원에 이미 지원한 이력이 있는지 확인 (대기/승인 상태)
     */
    @Query("SELECT a FROM KindergartenApplication a WHERE a.teacher.id = :teacherId AND a.kindergarten.id = :kindergartenId AND a.status IN ('PENDING', 'APPROVED') AND a.deletedAt IS NULL")
    Optional<KindergartenApplication> findActiveApplicationByTeacherAndKindergarten(
            @Param("teacherId") Long teacherId,
            @Param("kindergartenId") Long kindergartenId
    );

    /**
     * 교사-유치원 지원서 조회 (상태 무관, soft delete 제외)
     */
    @Query("SELECT a FROM KindergartenApplication a WHERE a.teacher.id = :teacherId AND a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL")
    Optional<KindergartenApplication> findByTeacherAndKindergarten(
            @Param("teacherId") Long teacherId,
            @Param("kindergartenId") Long kindergartenId
    );

    /**
     * 특정 상태의 지원서 목록
     */
    List<KindergartenApplication> findByTeacherIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(Long teacherId, ApplicationStatus status);

    /**
     * 유치원별 모든 지원서 목록
     */
    @Query("SELECT a FROM KindergartenApplication a WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL ORDER BY a.createdAt DESC")
    List<KindergartenApplication> findAllByKindergartenId(@Param("kindergartenId") Long kindergartenId);

    /**
     * 교사의 대기 중인 지원서가 있는지 확인
     */
    boolean existsByTeacherIdAndStatusAndDeletedAtIsNull(Long teacherId, ApplicationStatus status);

    @Query("SELECT a FROM KindergartenApplication a WHERE a.teacher.id = :teacherId AND a.status = 'PENDING' AND a.deletedAt IS NULL")
    List<KindergartenApplication> findPendingApplicationsByTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT a FROM KindergartenApplication a WHERE a.id = :applicationId AND a.deletedAt IS NULL")
    Optional<KindergartenApplication> findByIdAndDeletedAtIsNull(@Param("applicationId") Long applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM KindergartenApplication a WHERE a.id = :applicationId AND a.deletedAt IS NULL")
    Optional<KindergartenApplication> findByIdAndDeletedAtIsNullForUpdate(@Param("applicationId") Long applicationId);
}
