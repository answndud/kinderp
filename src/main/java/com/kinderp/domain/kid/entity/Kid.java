package com.kinderp.domain.kid.entity;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.common.BaseEntity;
import com.kinderp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 원생 엔티티
 */
@Entity
@Table(name = "kid")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Kid extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 소속 반
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    /**
     * 이름
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 생년월일
     */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /**
     * 성별
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    /**
     * 입소일
     */
    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    /**
     * 삭제일 (Soft Delete)
     */
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    /**
     * 학부모 목록 (연관관계의 주인)
     */
    @OneToMany(mappedBy = "kid", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParentKid> parents = new ArrayList<>();

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 원생 생성
     */
    public static Kid create(Classroom classroom, String name, LocalDate birthDate,
                             Gender gender, LocalDate admissionDate) {
        Kid kid = new Kid();
        kid.classroom = classroom;
        kid.name = name;
        kid.birthDate = birthDate;
        kid.gender = gender;
        kid.admissionDate = admissionDate;
        return kid;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 원생 정보 수정
     */
    public void update(String name, LocalDate birthDate, Gender gender) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
    }

    /**
     * 반 배정 변경
     */
    public void assignClassroom(Classroom classroom) {
        this.classroom = classroom;
    }

    /**
     * 학부모 연결
     */
    public void addParent(Member parent, Relationship relationship) {
        ParentKid parentKid = ParentKid.create(this, parent, relationship);
        parents.add(parentKid);
    }

    /**
     * 학부모 연결 해제
     */
    public void removeParent(Member parent) {
        parents.removeIf(pk -> pk.getParent().equals(parent));
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
     * 나이 계산 (만나이)
     */
    public int getAge() {
        LocalDate now = ProductTime.today();
        int age = now.getYear() - birthDate.getYear();
        if (now.getMonthValue() < birthDate.getMonthValue() ||
            (now.getMonthValue() == birthDate.getMonthValue() && now.getDayOfMonth() < birthDate.getDayOfMonth())) {
            age--;
        }
        return age;
    }

    /**
     * 특정 학부모와 연결되어 있는지 확인
     */
    public boolean hasParent(Member parent) {
        return parents.stream()
                .anyMatch(pk -> pk.getParent().equals(parent));
    }
}
