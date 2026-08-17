package com.kinderp.domain.notepad.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림장 등록/수정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class NotepadRequest {

    /**
     * 반 ID (null이면 전체 알림장)
     */
    private Long classroomId;

    /**
     * 원생 ID (null이면 반 전체 알림장)
     */
    private Long kidId;

    /**
     * 제목
     */
    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다")
    private String title;

    /**
     * 내용
     */
    @NotBlank(message = "내용은 필수입니다")
    private String content;

    /**
     * 사진 URL (복수일 경우 콤마로 구분)
     */
    private String photoUrl;
}
