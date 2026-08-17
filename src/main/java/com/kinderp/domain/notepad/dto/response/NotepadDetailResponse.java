package com.kinderp.domain.notepad.dto.response;

import com.kinderp.domain.notepad.entity.NotepadReadConfirm;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림장 상세 정보 응답 DTO (읽음 확인 포함)
 */
public record NotepadDetailResponse(
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
        List<ReaderInfo> readers,
        int readCount,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 읽은 사람 정보
     */
    public record ReaderInfo(
            Long readerId,
            String readerName,
            LocalDateTime readAt
    ) {
        public static ReaderInfo from(NotepadReadConfirm confirm) {
            return new ReaderInfo(
                    confirm.getReader().getId(),
                    confirm.getReader().getName(),
                    confirm.getReadAt()
            );
        }
    }

    /**
     * Notepad 엔티티를 DTO로 변환
     */
    public static NotepadDetailResponse from(com.kinderp.domain.notepad.entity.Notepad notepad,
                                              List<NotepadReadConfirm> readConfirms,
                                              boolean isRead) {
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

        List<ReaderInfo> readers = readConfirms.stream()
                .map(ReaderInfo::from)
                .toList();

        return new NotepadDetailResponse(
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
                readers,
                readConfirms.size(),
                isRead,
                notepad.getCreatedAt(),
                notepad.getUpdatedAt()
        );
    }

    /**
     * 읽음 확인 없이 변환
     */
    public static NotepadDetailResponse from(com.kinderp.domain.notepad.entity.Notepad notepad,
                                              List<NotepadReadConfirm> readConfirms) {
        return from(notepad, readConfirms, false);
    }
}
