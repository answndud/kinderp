package com.kinderp.domain.notepad.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림장 정보 응답 DTO
 */
public record NotepadResponse(
        Long id,
        Long classroomId,
        String classroomName,
        Long kidId,
        String kidName,
        Long writerId,
        String writerName,
        String title,
        String content,
        String photoUrl,
        List<String> photoUrls,
        int readCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Notepad 엔티티를 DTO로 변환
     */
    public static NotepadResponse from(com.kinderp.domain.notepad.entity.Notepad notepad, int readCount) {
        // 사진 URL 파싱
        List<String> photoUrls = List.of();
        if (notepad.getPhotoUrl() != null && !notepad.getPhotoUrl().isBlank()) {
            photoUrls = List.of(notepad.getPhotoUrl().split(","));
        }

        Long classroomId = null;
        String classroomName = null;
        if (notepad.getClassroom() != null) {
            classroomId = notepad.getClassroom().getId();
            classroomName = notepad.getClassroom().getName();
        }

        Long kidId = null;
        String kidName = null;
        if (notepad.getKid() != null) {
            kidId = notepad.getKid().getId();
            kidName = notepad.getKid().getName();
        }

        return new NotepadResponse(
                notepad.getId(),
                classroomId,
                classroomName,
                kidId,
                kidName,
                notepad.getWriter().getId(),
                notepad.getWriter().getName(),
                notepad.getTitle(),
                notepad.getContent(),
                notepad.getPhotoUrl(),
                photoUrls,
                readCount,
                notepad.getCreatedAt(),
                notepad.getUpdatedAt()
        );
    }

    /**
     * 읽음 확인 없이 변환
     */
    public static NotepadResponse from(com.kinderp.domain.notepad.entity.Notepad notepad) {
        return from(notepad, 0);
    }
}
