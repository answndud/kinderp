package com.kinderp.domain.notepad.repository;

import com.kinderp.domain.notepad.entity.Notepad;
import com.kinderp.domain.notepad.entity.NotepadReadConfirm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 알림장 리포지토리
 */
@Repository
public interface NotepadRepository extends JpaRepository<Notepad, Long> {

    interface NotepadReadCount {
        Long getNotepadId();
        long getReadCount();
    }

    /**
     * 유치원별 알림장 목록 조회 (최신순)
     */
    @EntityGraph(attributePaths = {"classroom", "kid", "writer"})
    @Query("SELECT n FROM Notepad n " +
           "LEFT JOIN n.classroom c " +
           "LEFT JOIN n.writer w " +
           "WHERE (c.kindergarten.id = :kindergartenId) " +
           "   OR (n.classroom IS NULL AND w.kindergarten.id = :kindergartenId) " +
           "ORDER BY n.createdAt DESC")
    Page<Notepad> findByKindergartenId(@Param("kindergartenId") Long kindergartenId, Pageable pageable);

    /**
     * 반별 알림장 목록 조회 (최신순)
     */
    @Query("SELECT n FROM Notepad n " +
           "LEFT JOIN FETCH n.classroom " +
           "LEFT JOIN FETCH n.writer " +
           "WHERE n.classroom.id = :classroomId AND n.kid IS NULL " +
           "ORDER BY n.createdAt DESC")
    Page<Notepad> findClassroomNotepads(@Param("classroomId") Long classroomId, Pageable pageable);

    /**
     * 원생별 알림장 목록 조회 (최신순)
     */
    @Query("SELECT n FROM Notepad n " +
           "LEFT JOIN FETCH n.kid " +
           "LEFT JOIN FETCH n.classroom " +
           "LEFT JOIN FETCH n.writer " +
           "WHERE n.kid.id = :kidId " +
           "ORDER BY n.createdAt DESC")
    Page<Notepad> findKidNotepads(@Param("kidId") Long kidId, Pageable pageable);

    /**
     * 반별 + 원생별 알림장 (반 전체 + 내 원생)
     */
    @Query("SELECT n FROM Notepad n " +
           "LEFT JOIN FETCH n.classroom " +
           "LEFT JOIN FETCH n.kid " +
           "LEFT JOIN FETCH n.writer " +
           "WHERE (n.classroom.id = :classroomId AND n.kid IS NULL) OR n.kid.id = :kidId " +
           "ORDER BY n.createdAt DESC")
    Page<Notepad> findNotepadsForParent(@Param("classroomId") Long classroomId, @Param("kidId") Long kidId, Pageable pageable);

    /**
     * 학부모용 알림장 목록 (내 원생 전체 기준)
     */
    @EntityGraph(attributePaths = {"classroom", "kid", "writer"})
    @Query("SELECT n FROM Notepad n " +
           "WHERE (n.classroom.id IN :classroomIds AND n.kid IS NULL) OR (n.kid.id IN :kidIds) " +
           "ORDER BY n.createdAt DESC")
    Page<Notepad> findNotepadsForParentKids(
            @Param("classroomIds") List<Long> classroomIds,
            @Param("kidIds") List<Long> kidIds,
            Pageable pageable);
 
    /**
     * ID로 조회 (연관 엔티티 JOIN FETCH)
     */
    @Query("SELECT n FROM Notepad n " +
           "LEFT JOIN FETCH n.classroom " +
           "LEFT JOIN FETCH n.kid " +
           "LEFT JOIN FETCH n.writer " +
           "WHERE n.id = :id")
    Optional<Notepad> findById(@Param("id") Long id);

    /**
     * 특정 알림장의 읽음 확인 조회
     */
    @Query("SELECT rc FROM NotepadReadConfirm rc WHERE rc.notepad.id = :notepadId")
    List<NotepadReadConfirm> findReadConfirmsByNotepadId(@Param("notepadId") Long notepadId);

    @Query("SELECT rc.notepad.id AS notepadId, COUNT(rc) AS readCount " +
            "FROM NotepadReadConfirm rc " +
            "WHERE rc.notepad.id IN :notepadIds " +
            "GROUP BY rc.notepad.id")
    List<NotepadReadCount> countReadConfirmsByNotepadIds(@Param("notepadIds") List<Long> notepadIds);

    /**
     * 특정 학부모가 읽은 알림장 목록
     */
    @Query("SELECT rc.notepad FROM NotepadReadConfirm rc WHERE rc.reader.id = :readerId ORDER BY rc.readAt DESC")
    Page<Notepad> findReadNotepadsByReader(@Param("readerId") Long readerId, Pageable pageable);

    /**
     * 특정 학부모가 특정 알림장을 읽었는지 확인
     */
    @Query("SELECT rc FROM NotepadReadConfirm rc WHERE rc.notepad.id = :notepadId AND rc.reader.id = :readerId")
    Optional<NotepadReadConfirm> findByNotepadIdAndReaderId(@Param("notepadId") Long notepadId, @Param("readerId") Long readerId);

    /**
     * 작성자별 알림장 목록
     */
    @Query("SELECT n FROM Notepad n WHERE n.writer.id = :writerId ORDER BY n.createdAt DESC")
    Page<Notepad> findByWriterId(@Param("writerId") Long writerId, Pageable pageable);
}
