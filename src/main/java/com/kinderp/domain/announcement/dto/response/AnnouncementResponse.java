package com.kinderp.domain.announcement.dto.response;

import java.time.LocalDateTime;

/**
 * 공지사항 정보 응답 DTO
 */
public record AnnouncementResponse(
        Long id,
        Long kindergartenId,
        String kindergartenName,
        Long writerId,
        String writerName,
        String title,
        String content,
        boolean isImportant,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Announcement 엔티티를 DTO로 변환
     */
    public static AnnouncementResponse from(com.kinderp.domain.announcement.entity.Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getKindergarten().getId(),
                announcement.getKindergarten().getName(),
                announcement.getWriter().getId(),
                announcement.getWriter().getName(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.isImportant(),
                announcement.getViewCount(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt()
        );
    }
}
