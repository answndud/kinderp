package com.kinderp.global.common;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티의 기본 클래스
 * 생성일, 수정일을 자동으로 관리합니다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * 엔티티 생성일시
     * updatable = false로 생성 후 수정 불가
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 엔티티 수정일시
     * 수정 시 자동 갱신
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
