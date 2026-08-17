package com.kinderp.domain.notepad.entity;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 알림장 엔티티
 */
@Entity
@Table(name = "notepad")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notepad extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 반 (null이면 전체 알림장)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    /**
     * 원생 (null이면 반 전체 알림장)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kid_id")
    private Kid kid;

    /**
     * 작성자 (교사 또는 원장)
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
     * 사진 URL (복수일 경우 콤마로 구분)
     */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * 읽음 확인 목록
     */
    @OneToMany(mappedBy = "notepad", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotepadReadConfirm> readConfirms = new ArrayList<>();

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 반별 알림장 생성
     */
    public static Notepad createClassroomNotepad(Classroom classroom, Member writer, String title, String content) {
        Notepad notepad = new Notepad();
        notepad.classroom = classroom;
        notepad.writer = writer;
        notepad.title = title;
        notepad.content = content;
        return notepad;
    }

    /**
     * 원생별 알림장 생성
     */
    public static Notepad createKidNotepad(Kid kid, Member writer, String title, String content) {
        Notepad notepad = new Notepad();
        notepad.classroom = kid.getClassroom();
        notepad.kid = kid;
        notepad.writer = writer;
        notepad.title = title;
        notepad.content = content;
        return notepad;
    }

    /**
     * 전체 알림장 생성
     */
    public static Notepad createGlobalNotepad(Member writer, String title, String content) {
        Notepad notepad = new Notepad();
        notepad.writer = writer;
        notepad.title = title;
        notepad.content = content;
        return notepad;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 알림장 수정
     */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 사진 URL 추가
     */
    public void addPhotoUrl(String photoUrl) {
        if (this.photoUrl == null || this.photoUrl.isBlank()) {
            this.photoUrl = photoUrl;
        } else {
            this.photoUrl = this.photoUrl + "," + photoUrl;
        }
    }

    /**
     * 사진 URL 설정 (덮어쓰기)
     */
    public void setPhotoUrls(String photoUrls) {
        this.photoUrl = photoUrls;
    }

    /**
     * 읽음 확인 추가
     */
    public void addReadConfirm(Member reader) {
        NotepadReadConfirm confirm = NotepadReadConfirm.create(this, reader);
        readConfirms.add(confirm);
    }

    /**
     * 반별 알림장 여부
     */
    public boolean isClassroomNotepad() {
        return classroom != null && kid == null;
    }

    /**
     * 원생별 알림장 여부
     */
    public boolean isKidNotepad() {
        return kid != null;
    }

    /**
     * 전체 알림장 여부
     */
    public boolean isGlobalNotepad() {
        return classroom == null;
    }

    /**
     * 작성자 확인
     */
    public boolean isWriter(Member member) {
        return writer.equals(member);
    }
}
