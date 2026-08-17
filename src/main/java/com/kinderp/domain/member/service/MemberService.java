package com.kinderp.domain.member.service;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 이메일 중복 확인
     */
    public boolean checkEmailDuplicate(String email) {
        return memberRepository.existsByEmail(email);
    }

    /**
     * 회원가입
     */
    @Transactional
    public Long signUp(String email, String password, String name, String phone, MemberRole role) {
        // 이메일 중복 확인
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(password);

        // 회원 생성
        Member member = Member.create(email, encodedPassword, name, phone, role);

        // 선생님/학부모는 승인 전까지 PENDING
        if (role == MemberRole.TEACHER || role == MemberRole.PARENT) {
            member.markPending();
        }

        // 저장
        Member savedMember = memberRepository.save(member);

        return savedMember.getId();
    }

    /**
     * 이메일로 회원 조회
     */
    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * ID로 회원 조회
     */
    public Member getMemberById(Long id) {
        return memberRepository.findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 유치원별 다중 역할 회원 조회
     */
    public java.util.List<Member> getMembersByKindergartenAndRoles(Long kindergartenId, java.util.List<MemberRole> roles) {
        return memberRepository.findAllByKindergartenIdAndRoles(kindergartenId, roles);
    }

    /**
     * ID로 회원 조회 (유치원 포함)
     * 뷰에서 사용할 때 LazyInitializationException 방지용
     */
    public Member getMemberByIdWithKindergarten(Long id) {
        return memberRepository.findByIdWithKindergarten(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 프로필 수정
     */
    @Transactional
    public void updateProfile(Long memberId, String name, String phone) {
        Member member = getMemberById(memberId);
        member.updateProfile(name, phone);
    }

    /**
     * 비밀번호 변경
     */
    @Transactional
    public void changePassword(Long memberId, String oldPassword, String newPassword) {
        Member member = getMemberById(memberId);

        // 기존 비밀번호 검증
        if (!member.matchesPassword(oldPassword, passwordEncoder)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 새 비밀번호 암호화 및 변경
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        member.changePassword(encodedNewPassword);
    }

    /**
     * 소셜 전용 계정의 초기 로컬 비밀번호 설정
     */
    @Transactional
    public void setInitialPassword(Long memberId, String newPassword) {
        Member member = getMemberById(memberId);

        if (member.hasLocalPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_ALREADY_SET);
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        member.changePassword(encodedPassword);
    }

    /**
     * 회원 탈퇴
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = getMemberById(memberId);
        member.withdraw();
    }

    /**
     * 회원 활성화
     */
    @Transactional
    public void activateMember(Long memberId) {
        Member member = getMemberById(memberId);
        member.activate();
    }

    /**
     * 회원 비활성화
     */
    @Transactional
    public void deactivateMember(Long memberId) {
        Member member = getMemberById(memberId);
        member.deactivate();
    }
}
