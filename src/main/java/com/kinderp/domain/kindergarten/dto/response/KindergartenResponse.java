package com.kinderp.domain.kindergarten.dto.response;

import java.time.LocalDateTime;

/**
 * 유치원 정보 응답 DTO
 */
public record KindergartenResponse(
        Long id,
        String name,
        String address,
        String phone,
        String openTime,
        String closeTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Kindergarten 엔티티를 DTO로 변환
     */
    public static KindergartenResponse from(com.kinderp.domain.kindergarten.entity.Kindergarten kindergarten) {
        return new KindergartenResponse(
                kindergarten.getId(),
                kindergarten.getName(),
                kindergarten.getAddress(),
                kindergarten.getPhone(),
                kindergarten.getOpenTime() != null ? kindergarten.getOpenTime().toString() : null,
                kindergarten.getCloseTime() != null ? kindergarten.getCloseTime().toString() : null,
                kindergarten.getCreatedAt(),
                kindergarten.getUpdatedAt()
        );
    }
}
