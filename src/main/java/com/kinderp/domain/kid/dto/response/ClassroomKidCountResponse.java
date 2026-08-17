package com.kinderp.domain.kid.dto.response;

/**
 * 반별 원생 수 응답 DTO
 */
public record ClassroomKidCountResponse(
        Long classroomId,
        Long count
) {
    public static ClassroomKidCountResponse of(Long classroomId, Long count) {
        return new ClassroomKidCountResponse(classroomId, count);
    }
}
