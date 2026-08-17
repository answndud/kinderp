package com.kinderp.domain.announcement.repository;

import com.kinderp.domain.announcement.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 공지사항 리포지토리
 */
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    interface DashboardAnnouncementStatsProjection {
        long getTotalAnnouncements();

        long getUniqueReadCount();
    }

    /**
     * 유치원별 공지사항 목록 조회 (삭제되지 않은 것만, 최신순)
     */
    @Query(value = "SELECT a FROM Announcement a " +
            "LEFT JOIN FETCH a.kindergarten k " +
            "LEFT JOIN FETCH a.writer w " +
            "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL " +
            "ORDER BY a.isImportant DESC, a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM Announcement a " +
                   "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL")
    Page<Announcement> findByKindergartenIdAndDeletedAtIsNull(@Param("kindergartenId") Long kindergartenId, Pageable pageable);

    /**
     * 유치원별 중요 공지사항 목록 조회
     */
    @Query(value = "SELECT a FROM Announcement a " +
            "LEFT JOIN FETCH a.kindergarten k " +
            "LEFT JOIN FETCH a.writer w " +
            "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL AND a.isImportant = true " +
            "ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM Announcement a " +
                   "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL AND a.isImportant = true")
    Page<Announcement> findImportantByKindergartenId(@Param("kindergartenId") Long kindergartenId, Pageable pageable);

    /**
     * ID로 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT a FROM Announcement a LEFT JOIN FETCH a.kindergarten k LEFT JOIN FETCH a.writer w WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<Announcement> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    /**
     * 제목으로 검색 (유치원별)
     */
    @Query(value = "SELECT a FROM Announcement a " +
            "LEFT JOIN FETCH a.kindergarten k " +
            "LEFT JOIN FETCH a.writer w " +
            "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL AND a.title LIKE %:title% " +
            "ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM Announcement a " +
                   "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL AND a.title LIKE %:title%")
    Page<Announcement> findByKindergartenIdAndTitleContaining(@Param("kindergartenId") Long kindergartenId, @Param("title") String title, Pageable pageable);

    /**
     * 조회수 상위 공지사항
     */
    @Query(value = "SELECT a FROM Announcement a " +
            "LEFT JOIN FETCH a.kindergarten k " +
            "LEFT JOIN FETCH a.writer w " +
            "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL " +
            "ORDER BY a.viewCount DESC",
           countQuery = "SELECT COUNT(a) FROM Announcement a " +
                   "WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL")
    Page<Announcement> findMostViewedByKindergartenId(@Param("kindergartenId") Long kindergartenId, Pageable pageable);

    /**
     * ID로 조회 (연관 엔티티 포함)
     * 뷰에서 사용할 때 LazyInitializationException 방지용
     */
    @Query("SELECT a FROM Announcement a LEFT JOIN FETCH a.kindergarten k LEFT JOIN FETCH a.writer w WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<Announcement> findByIdWithRelations(@Param("id") Long id);

    /**
     * 유치원별 공지사항 목록 조회 (연관 엔티티 포함)
     * 뷰에서 사용할 때 LazyInitializationException 방지용
     */
    @Query("SELECT a FROM Announcement a LEFT JOIN FETCH a.kindergarten k LEFT JOIN FETCH a.writer w WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL ORDER BY a.isImportant DESC, a.createdAt DESC")
    java.util.List<Announcement> findByKindergartenIdWithRelations(@Param("kindergartenId") Long kindergartenId);

    /**
     * 유치원별 공지사항 수
     */
    @Query("SELECT COUNT(a) FROM Announcement a WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL")
    long countByKindergartenIdAndDeletedAtIsNull(@Param("kindergartenId") Long kindergartenId);

    @Query(value = """
            SELECT COUNT(DISTINCT a.id) AS totalAnnouncements,
                   COUNT(av.id) AS uniqueReadCount
            FROM announcement a
            LEFT JOIN announcement_view av ON av.announcement_id = a.id
            WHERE a.kindergarten_id = :kindergartenId
              AND a.deleted_at IS NULL
            """, nativeQuery = true)
    DashboardAnnouncementStatsProjection findDashboardStats(@Param("kindergartenId") Long kindergartenId);

    /**
     * 유치원별 공지사항 목록 조회 (리스트)
     */
    @Query("SELECT a FROM Announcement a WHERE a.kindergarten.id = :kindergartenId AND a.deletedAt IS NULL ORDER BY a.isImportant DESC, a.createdAt DESC")
    java.util.List<Announcement> findByKindergartenIdAndDeletedAtIsNull(@Param("kindergartenId") Long kindergartenId);
}
