package com.kinderp.domain.notepad.entity;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림장 읽음 확인 엔티티
 */
@Entity
@Table(name = "notepad_read_confirm", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"notepad_id", "reader_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotepadReadConfirm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 알림장
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notepad_id", nullable = false)
    private Notepad notepad;

    /**
     * 읽은 사람 (학부모)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reader_id", nullable = false)
    private Member reader;

    /**
     * 읽은 시간
     */
    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 읽음 확인 생성
     */
    public static NotepadReadConfirm create(Notepad notepad, Member reader) {
        NotepadReadConfirm confirm = new NotepadReadConfirm();
        confirm.notepad = notepad;
        confirm.reader = reader;
        confirm.readAt = ProductTime.nowDateTime();
        return confirm;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 읽은 시간 갱신
     */
    public void updateReadTime() {
        this.readAt = ProductTime.nowDateTime();
    }
}
