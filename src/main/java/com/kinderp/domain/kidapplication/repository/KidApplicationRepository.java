package com.kinderp.domain.kidapplication.repository;

import com.kinderp.domain.kidapplication.entity.ApplicationStatus;
import com.kinderp.domain.kidapplication.entity.KidApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KidApplicationRepository extends JpaRepository<KidApplication, Long> {

    /**
     * 특정 유치원에 대한 학부모의 대기 중인 신청
     */
    @Query("SELECT a FROM KidApplication a WHERE a.parent.id = :parentId AND a.kindergarten.id = :kindergartenId AND a.status = 'PENDING' AND a.deletedAt IS NULL")
    Optional<KidApplication> findPendingApplicationByParentAndKindergarten(
            @Param("parentId") Long parentId,
            @Param("kindergartenId") Long kindergartenId
    );

    @Query("""
            SELECT a
            FROM KidApplication a
            WHERE a.parent.id = :parentId
              AND a.kindergarten.id = :kindergartenId
              AND a.status IN :statuses
              AND a.deletedAt IS NULL
            """)
    Optional<KidApplication> findActiveApplicationByParentAndKindergarten(@Param("parentId") Long parentId,
                                                                          @Param("kindergartenId") Long kindergartenId,
                                                                          @Param("statuses") Collection<ApplicationStatus> statuses);

    /**
     * 학부모-유치원 신청 조회 (상태 무관, soft delete 제외)
     */
    @Query("SELECT a FROM KidApplication a WHERE a.parent.id = :parentId AND a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL")
    Optional<KidApplication> findByParentAndKindergarten(
            @Param("parentId") Long parentId,
            @Param("kindergartenId") Long kindergartenId
    );

    /**
     * 학부모의 대기 중인 입학 신청 존재 여부
     */
    boolean existsByParentIdAndStatusAndDeletedAtIsNull(Long parentId, ApplicationStatus status);

    boolean existsByParentIdAndStatusInAndDeletedAtIsNull(Long parentId, Collection<ApplicationStatus> statuses);


    /**
     * 학부모의 입학 신청 목록
     */
    List<KidApplication> findByParentIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long parentId);

    /**
     * 유치원별 대기 중인 입학 신청 목록
     */
    @Query("SELECT a FROM KidApplication a WHERE a.kindergarten.id = :kindergartenId AND a.status = 'PENDING' AND a.deletedAt IS NULL ORDER BY a.createdAt ASC")
    List<KidApplication> findPendingApplicationsByKindergartenId(@Param("kindergartenId") Long kindergartenId);

    @Query("""
            SELECT a
            FROM KidApplication a
            WHERE a.kindergarten.id = :kindergartenId
              AND a.status IN :statuses
              AND a.deletedAt IS NULL
            ORDER BY CASE a.status
                        WHEN com.kinderp.domain.kidapplication.entity.ApplicationStatus.PENDING THEN 0
                        WHEN com.kinderp.domain.kidapplication.entity.ApplicationStatus.WAITLISTED THEN 1
                        WHEN com.kinderp.domain.kidapplication.entity.ApplicationStatus.OFFERED THEN 2
                        ELSE 3
                     END ASC,
                     a.createdAt ASC,
                     a.id ASC
            """)
    List<KidApplication> findReviewQueueByKindergartenId(@Param("kindergartenId") Long kindergartenId,
                                                         @Param("statuses") Collection<ApplicationStatus> statuses);

    /**
     * 특정 상태의 입학 신청 목록
     */
    List<KidApplication> findByParentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(Long parentId, ApplicationStatus status);

    /**
     * 유치원별 모든 입학 신청 목록
     */
    @Query("SELECT a FROM KidApplication a WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL ORDER BY a.createdAt DESC")
    List<KidApplication> findAllByKindergartenId(@Param("kindergartenId") Long kindergartenId);

    /**
     * 입학 신청 상세 조회
     */
    @Query("SELECT a FROM KidApplication a WHERE a.id = :applicationId AND a.deletedAt IS NULL")
    Optional<KidApplication> findByIdAndDeletedAtIsNull(@Param("applicationId") Long applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM KidApplication a WHERE a.id = :applicationId AND a.deletedAt IS NULL")
    Optional<KidApplication> findByIdAndDeletedAtIsNullForUpdate(@Param("applicationId") Long applicationId);

    @Query("""
            SELECT COUNT(a)
            FROM KidApplication a
            WHERE a.assignedClassroom.id = :classroomId
              AND a.status = :status
              AND a.deletedAt IS NULL
              AND (a.offerExpiresAt IS NULL OR a.offerExpiresAt > :now)
            """)
    long countActiveOffersByAssignedClassroomId(@Param("classroomId") Long classroomId,
                                                @Param("status") ApplicationStatus status,
                                                @Param("now") LocalDateTime now);

    /** 정원 검증용 잠금 읽기 (KidRepository.countActiveByClassroomIdForShare 참고). */
    @Query(value = """
            SELECT COUNT(*)
            FROM kid_application
            WHERE assigned_classroom_id = :classroomId
              AND status = 'OFFERED'
              AND deleted_at IS NULL
              AND (offer_expires_at IS NULL OR offer_expires_at > :now)
            FOR SHARE
            """, nativeQuery = true)
    long countActiveOffersByAssignedClassroomIdForShare(@Param("classroomId") Long classroomId,
                                                        @Param("now") LocalDateTime now);

    /**
     * 만료된 offer를 잠그며 가져온다. 다른 트랜잭션(수락·취소·다른 인스턴스의 스케줄러)이 이미 잠근 행은 건너뛰므로
     * 만료 처리가 진행 중인 수락 결과를 덮어쓰거나 같은 건을 두 번 처리하지 않는다.
     */
    @Query(value = """
            SELECT id
            FROM kid_application
            WHERE status = 'OFFERED'
              AND offer_expires_at <= :now
              AND deleted_at IS NULL
            ORDER BY offer_expires_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> claimExpiredOfferIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
