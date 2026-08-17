package com.kinderp.domain.member.repository;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 회원 리포지토리
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    interface DashboardMemberCountsProjection {
        long getTotalTeachers();

        long getTotalParents();

        long getTotalMembers();
    }

    /**
     * 이메일로 회원 조회
     */
    @Query("SELECT m FROM Member m WHERE LOWER(m.email) = LOWER(:email) AND m.deletedAt IS NULL")
    Optional<Member> findByEmail(@Param("email") String email);

    @Query("SELECT m FROM Member m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<Member> findActiveById(@Param("id") Long id);

    @Query("""
            SELECT m.kindergarten.id
            FROM Member m
            WHERE m.deletedAt IS NULL
              AND m.id = :id
              AND m.kindergarten IS NOT NULL
            """)
    Optional<Long> findKindergartenIdById(@Param("id") Long id);

    @Query("""
            SELECT m.kindergarten.id
            FROM Member m
            WHERE m.deletedAt IS NULL
              AND LOWER(m.email) = LOWER(:email)
              AND m.kindergarten IS NOT NULL
            """)
    Optional<Long> findKindergartenIdByEmail(@Param("email") String email);

    @Query("""
            SELECT DISTINCT m
            FROM Member m
            LEFT JOIN FETCH m.kindergarten
            LEFT JOIN FETCH m.socialAccounts
            WHERE m.deletedAt IS NULL
              AND m.id = :id
            """)
    Optional<Member> findByIdWithKindergartenAndSocialAccounts(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM Member m
            LEFT JOIN FETCH m.kindergarten
            WHERE m.deletedAt IS NULL
              AND m.id = :id
            """)
    Optional<Member> findByIdWithKindergartenForUpdate(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT m
            FROM Member m
            LEFT JOIN FETCH m.socialAccounts
            WHERE m.deletedAt IS NULL
              AND m.id = :id
            """)
    Optional<Member> findByIdWithSocialAccounts(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT m
            FROM Member m
            LEFT JOIN FETCH m.kindergarten
            LEFT JOIN FETCH m.socialAccounts
            WHERE m.deletedAt IS NULL
              AND m.id IN (
                    SELECT msa.member.id
                    FROM MemberSocialAccount msa
                    WHERE msa.provider = :provider
                      AND msa.providerId = :providerId
                      AND msa.unlinkedAt IS NULL
              )
            """)
    Optional<Member> findBySocialProviderAndProviderId(@Param("provider") MemberAuthProvider provider,
                                                        @Param("providerId") String providerId);

    @Query("""
            SELECT DISTINCT m
            FROM Member m
            LEFT JOIN FETCH m.socialAccounts
            WHERE m.id IN (
                    SELECT msa.member.id
                    FROM MemberSocialAccount msa
                    WHERE msa.provider = :provider
                      AND msa.providerId = :providerId
              )
            """)
    Optional<Member> findByAnySocialProviderAndProviderId(@Param("provider") MemberAuthProvider provider,
                                                          @Param("providerId") String providerId);

    /**
     * 이메일 중복 확인
     */
    boolean existsByEmail(String email);

    /**
     * 이메일과 활성 상태로 회원 조회
     */
    Optional<Member> findByEmailAndStatus(String email, MemberStatus status);

    /**
     * 역할별 회원 목록 조회
     */
    List<Member> findByRole(MemberRole role);

    /**
     * 유치원별 회원 목록 조회
     */
    @Query("SELECT m FROM Member m WHERE m.kindergarten.id = :kindergartenId AND m.status = :status")
    List<Member> findByKindergartenIdAndStatus(@Param("kindergartenId") Long kindergartenId,
                                                @Param("status") MemberStatus status);

    /**
     * 유치원별 특정 역할 회원 조회 (원장 찾기 등)
     */
    @Query("SELECT m FROM Member m WHERE m.kindergarten.id = :kindergartenId AND m.role = :role AND m.deletedAt IS NULL")
    Optional<Member> findByKindergartenIdAndRole(@Param("kindergartenId") Long kindergartenId,
                                                  @Param("role") MemberRole role);

    /**
     * 유치원별 특정 역할 회원 목록
     */
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.kindergarten k WHERE k.id = :kindergartenId AND m.role = :role AND m.deletedAt IS NULL")
    List<Member> findAllByKindergartenIdAndRole(@Param("kindergartenId") Long kindergartenId,
                                                   @Param("role") MemberRole role);

    /**
     * 유치원별 다중 역할 회원 목록
     */
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.kindergarten k WHERE k.id = :kindergartenId AND m.role IN :roles AND m.deletedAt IS NULL")
    List<Member> findAllByKindergartenIdAndRoles(@Param("kindergartenId") Long kindergartenId,
                                                  @Param("roles") List<MemberRole> roles);

    /**
     * ID로 회원 조회 (유치원 포함)
     * 뷰에서 사용할 때 LazyInitializationException 방지용
     */
    @Query("SELECT DISTINCT m FROM Member m LEFT JOIN FETCH m.kindergarten LEFT JOIN FETCH m.socialAccounts WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<Member> findByIdWithKindergarten(@Param("id") Long id);

    /**
     * 유치원별 특정 역할 회원 수
     */
    @Query("SELECT COUNT(m) FROM Member m WHERE m.kindergarten.id = :kindergartenId AND m.role = :role AND m.deletedAt IS NULL")
    long countByKindergartenIdAndRole(@Param("kindergartenId") Long kindergartenId, @Param("role") MemberRole role);

    /**
     * 유치원별 총 회원 수
     */
    @Query("SELECT COUNT(m) FROM Member m WHERE m.kindergarten.id = :kindergartenId AND m.deletedAt IS NULL")
    long countByKindergartenIdAndDeletedAtIsNull(@Param("kindergartenId") Long kindergartenId);

    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN m.role = com.kinderp.domain.member.entity.MemberRole.TEACHER THEN 1 ELSE 0 END), 0) AS totalTeachers, " +
            "COALESCE(SUM(CASE WHEN m.role = com.kinderp.domain.member.entity.MemberRole.PARENT THEN 1 ELSE 0 END), 0) AS totalParents, " +
            "COUNT(m) AS totalMembers " +
            "FROM Member m " +
            "WHERE m.kindergarten.id = :kindergartenId AND m.deletedAt IS NULL")
    DashboardMemberCountsProjection findDashboardMemberCounts(@Param("kindergartenId") Long kindergartenId);
}
