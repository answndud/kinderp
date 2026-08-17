package com.kinderp.domain.kid.dto.request;

import com.kinderp.domain.kid.entity.Relationship;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학부모 연결 요청 DTO
 */
@Getter
@NoArgsConstructor
public class AssignParentRequest {

    /**
     * 학부모 ID
     */
    @NotNull(message = "학부모 ID는 필수입니다")
    private Long parentId;

    /**
     * 관계
     */
    @NotNull(message = "관계는 필수입니다")
    private Relationship relationship;
}
