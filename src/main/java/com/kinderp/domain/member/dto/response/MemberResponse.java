package com.kinderp.domain.member.dto.response;

import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;

import java.time.LocalDateTime;

/**
 * 회원 정보 응답 DTO
 */
public record MemberResponse(
        Long id,
        String email,
        String name,
        String phone,
        MemberRole role,
        MemberStatus status,
        Long kindergartenId,
        String kindergartenName,
        LocalDateTime createdAt
) {
    /**
     * Member 엔티티를 DTO로 변환
     */
    public static MemberResponse from(com.kinderp.domain.member.entity.Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getPhone(),
                member.getRole(),
                member.getStatus(),
                member.getKindergarten() != null ? member.getKindergarten().getId() : null,
                member.getKindergarten() != null ? member.getKindergarten().getName() : null,
                member.getCreatedAt()
        );
    }
}
