package com.kinderp.domain.kid.entity;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학부모-원생 연결 엔티티 (중간 테이블)
 */
@Entity
@Table(name = "parent_kid", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"parent_id", "kid_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentKid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 원생
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kid_id", nullable = false)
    private Kid kid;

    /**
     * 학부모
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Member parent;

    /**
     * 관계
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 20)
    private Relationship relationship;

    /**
     * 생성일
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 학부모-원생 연결 생성
     */
    public static ParentKid create(Kid kid, Member parent, Relationship relationship) {
        ParentKid parentKid = new ParentKid();
        parentKid.kid = kid;
        parentKid.parent = parent;
        parentKid.relationship = relationship;
        parentKid.createdAt = ProductTime.nowDateTime();
        return parentKid;
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 관계 변경
     */
    public void changeRelationship(Relationship relationship) {
        this.relationship = relationship;
    }
}
