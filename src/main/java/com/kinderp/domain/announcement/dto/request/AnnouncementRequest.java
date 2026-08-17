package com.kinderp.domain.announcement.dto.request;

import com.kinderp.domain.member.entity.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공지사항 등록/수정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class AnnouncementRequest {

    /**
     * 유치원 ID
     */
    @NotNull(message = "유치원 ID는 필수입니다")
    private Long kindergartenId;

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
     * 중요 공지 여부
     */
    private Boolean isImportant;

    /**
     * 알림 대상 역할 (선택, 없으면 전체)
     */
    private List<MemberRole> targetRoles;
}
