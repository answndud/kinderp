package com.kinderp.domain.member.controller;

import com.kinderp.domain.member.dto.response.MemberResponse;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import com.kinderp.domain.auth.service.AuthService;
import com.kinderp.domain.auth.service.SocialAccountLinkService;
import com.kinderp.domain.authaudit.service.AuthAuditLogService;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.global.common.ApiResponse;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.ClientIpResolver;
import com.kinderp.global.security.user.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;
    private final AuthService authService;
    private final SocialAccountLinkService socialAccountLinkService;
    private final AuthAuditLogService authAuditLogService;
    private final ClientIpResolver clientIpResolver;

    /**
     * 내 프로필 조회
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Member member = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(MemberResponse.from(member)));
    }

    /**
     * 학부모 목록 조회 (원장/교사)
     */
    @GetMapping("/parents")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ApiResponse<java.util.List<MemberResponse>>> getParents(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Member member = memberService.getMemberById(userDetails.getMemberId());
        if (member.getKindergarten() == null) {
            return ResponseEntity.ok(ApiResponse.success(java.util.List.of()));
        }

        java.util.List<Member> parents = memberService.getMembersByKindergartenAndRoles(
                member.getKindergarten().getId(),
                java.util.List.of(com.kinderp.domain.member.entity.MemberRole.PARENT)
        );

        java.util.List<MemberResponse> responses = new java.util.ArrayList<>();
        for (Member parent : parents) {
            responses.add(MemberResponse.from(parent));
        }

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 프로필 수정
     */
    @PatchMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MemberResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        memberService.updateProfile(userDetails.getMemberId(), request.getName(), request.getPhone());
        Member updatedMember = memberService.getMemberByIdWithKindergarten(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(MemberResponse.from(updatedMember)));
    }

    /**
     * 비밀번호 변경
     */
    @PatchMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        memberService.changePassword(userDetails.getMemberId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 소셜 전용 계정의 초기 로컬 비밀번호 설정
     */
    @PostMapping("/password/bootstrap")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> bootstrapPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BootstrapPasswordRequest request
    ) {
        memberService.setInitialPassword(userDetails.getMemberId(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/social-link/{provider}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unlinkSocialAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String provider,
            HttpServletRequest request
    ) {
        MemberAuthProvider resolvedProvider = resolveProvider(provider);
        String clientIp = clientIpResolver.resolve(request);

        try {
            socialAccountLinkService.unlinkSocialAccount(userDetails.getMemberId(), resolvedProvider);
            authAuditLogService.recordSocialUnlinkSuccess(
                    userDetails.getMemberId(),
                    userDetails.getUsername(),
                    resolvedProvider,
                    clientIp
            );
        } catch (BusinessException ex) {
            authAuditLogService.recordSocialUnlinkFailure(
                    userDetails.getMemberId(),
                    userDetails.getUsername(),
                    resolvedProvider,
                    clientIp,
                    ex.getErrorCode().getCode()
            );
            throw ex;
        }

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 회원 탈퇴
     */
    @DeleteMapping("/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse response
    ) {
        memberService.withdraw(userDetails.getMemberId());
        authService.revokeAllSessions(userDetails.getMemberId(), response);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ========== Request DTO ==========

    @Data
    public static class UpdateProfileRequest {
        @NotBlank(message = "이름은 필수입니다")
        @Size(min = 2, max = 50, message = "이름은 2~50자여야 합니다")
        private String name;

        @Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
        private String phone;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "현재 비밀번호는 필수입니다")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
        private String newPassword;
    }

    @Data
    public static class BootstrapPasswordRequest {
        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
        private String newPassword;
    }

    private MemberAuthProvider resolveProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            MemberAuthProvider resolved = MemberAuthProvider.valueOf(provider.trim().toUpperCase());
            if (resolved == MemberAuthProvider.LOCAL) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return resolved;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
