package com.kinderp.domain.auth.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토큰 응답 DTO
 */
@Getter
@NoArgsConstructor
public class TokenResponse {

    /**
     * 액세스 토큰 (쿠키에 저장되므로 본문에 포함 안 해도 됨)
     */
    private String accessToken;

    /**
     * 리프레시 토큰 (쿠키에 저장되므로 본문에 포함 안 해도 됨)
     */
    private String refreshToken;

    public TokenResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
