package com.kinderp.domain.classroom.entity;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.common.BaseEntity;
import com.kinderp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 반 엔티티
 */
@Entity
@Table(name = "classroom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Classroom extends BaseEntity {

    public static final int DEFAULT_CAPACITY = 20;

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
     * 반 이름 (예: 해바라기반, 무지개반)
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 연령대 (예: 5세반, 6세반, 7세반)
     */
    @Column(name = "age_group", length = 20)
    private String ageGroup;

    /**
     * 정원
     */
    @Column(name = "capacity", nullable = false)
    private Integer capacity = DEFAULT_CAPACITY;

    /**
     * 담임 교사
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Member teacher;

    /**
     * 삭제일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 반 생성
     */
    public static Classroom create(Kindergarten kindergarten, String name, String ageGroup) {
        return create(kindergarten, name, ageGroup, DEFAULT_CAPACITY);
    }

    public static Classroom create(Kindergarten kindergarten, String name, String ageGroup, Integer capacity) {
        Classroom classroom = new Classroom();
        classroom.kindergarten = kindergarten;
        classroom.name = name;
        classroom.ageGroup = ageGroup;
        classroom.capacity = capacity == null ? DEFAULT_CAPACITY : capacity;
        return classroom;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 반 정보 수정
     */
    public void update(String name, String ageGroup) {
        update(name, ageGroup, this.capacity);
    }

    public void update(String name, String ageGroup, Integer capacity) {
        this.name = name;
        this.ageGroup = ageGroup;
        this.capacity = capacity == null ? DEFAULT_CAPACITY : capacity;
    }

    /**
     * 담임 교사 배정
     */
    public void assignTeacher(Member teacher) {
        this.teacher = teacher;
    }

    /**
     * 담임 교사 해제
     */
    public void removeTeacher() {
        this.teacher = null;
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
     * 교사 배정 가능 여부
     */
    public boolean canAssignTeacher() {
        return this.teacher == null;
    }

    /**
     * 삭제 가능 여부 (원생이 있는지 확인)
     */
    public boolean canDelete(long kidsCount) {
        return this.deletedAt == null && kidsCount == 0;
    }

    public long remainingSeats(long occupiedSeats) {
        return Math.max(0, (long) capacity - occupiedSeats);
    }

    public boolean canResizeTo(long occupiedSeats, int requestedCapacity) {
        return requestedCapacity >= occupiedSeats;
    }
}
