package com.erp.domain.announcement.entity;

import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.member.entity.Member;
import com.erp.global.common.BaseEntity;
import com.erp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항 엔티티
 */
@Entity
@Table(name = "announcement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 유치원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    /**
     * 작성자 (원장 또는 교사)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id", nullable = false)
    private Member writer;

    /**
     * 제목
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 내용
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 중요 공지 여부
     */
    @Column(name = "is_important")
    private Boolean isImportant = false;

    /**
     * 조회수
     */
    @Column(name = "view_count")
    private Integer viewCount = 0;

    /**
     * 삭제일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 공지사항 생성
     */
    public static Announcement create(Kindergarten kindergarten, Member writer, String title, String content) {
        Announcement announcement = new Announcement();
        announcement.kindergarten = kindergarten;
        announcement.writer = writer;
        announcement.title = title;
        announcement.content = content;
        announcement.isImportant = false;
        announcement.viewCount = 0;
        return announcement;
    }

    /**
     * 중요 공지사항 생성
     */
    public static Announcement createImportant(Kindergarten kindergarten, Member writer, String title, String content) {
        Announcement announcement = create(kindergarten, writer, title, content);
        announcement.isImportant = true;
        return announcement;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 공지사항 수정
     */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 중요 공지 설정
     */
    public void setImportant(boolean important) {
        this.isImportant = important;
    }

    /**
     * 중요 공지 토글
     */
    public void toggleImportant() {
        this.isImportant = !this.isImportant;
    }

    /**
     * 조회수 증가
     */
    public void incrementViewCount() {
        this.viewCount++;
    }

    /**
     * Soft Delete
     */
    public void softDelete() {
        this.deletedAt = ProductTime.nowDateTime();
    }

    /**
     * 삭제 복구
     */
    public void restore() {
        this.deletedAt = null;
    }

    /**
     * 작성자 확인
     */
    public boolean isWriter(Member member) {
        return writer.equals(member);
    }

    /**
     * 중요 공지 여부
     */
    public boolean isImportant() {
        return this.isImportant != null && this.isImportant;
    }
}
