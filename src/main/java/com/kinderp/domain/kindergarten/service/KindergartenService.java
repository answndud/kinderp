package com.kinderp.domain.kindergarten.service;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.security.access.AccessPolicyService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 유치원 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KindergartenService {

    private final KindergartenRepository kindergartenRepository;
    private final MemberRepository memberRepository;
    private final AccessPolicyService accessPolicyService;

    /**
     * 유치원 등록
     */
    @Transactional
    public Long register(String name, String address, String phone,
                         String openTime, String closeTime, Long principalId) {
        // 유치원명 중복 확인 (선택)
        if (kindergartenRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ALREADY_EXISTS);
        }

        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (principal.getRole() != MemberRole.PRINCIPAL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (principal.getKindergarten() != null) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED_TO_KINDERGARTEN);
        }

        // 시간 변환
        java.time.LocalTime open = parseTime(openTime);
        java.time.LocalTime close = parseTime(closeTime);

        // 유치원 생성
        Kindergarten kindergarten = Kindergarten.create(name, address, phone, open, close);

        // 저장
        Kindergarten saved = kindergartenRepository.save(kindergarten);
        principal.assignKindergarten(saved);
        memberRepository.save(principal);

        return saved.getId();
    }

    /**
     * 유치원 조회
     */
    public Kindergarten getKindergarten(Long id) {
        return kindergartenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND));
    }

    public Kindergarten getKindergartenForRequester(Long id, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, id);
        return getKindergarten(id);
    }

    /**
     * 전체 유치원 조회
     */
    public List<Kindergarten> getAllKindergartens() {
        return kindergartenRepository.findAllByOrderByNameAsc();
    }

    public List<Kindergarten> getKindergartensForRequester(Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        if (requester.getKindergarten() == null) {
            return getAllKindergartens();
        }
        return List.of(requester.getKindergarten());
    }

    /**
     * 유치원 수정
     */
    @Transactional
    public void updateKindergarten(Long id, String name, String address, String phone,
                                   String openTime, String closeTime) {
        Kindergarten kindergarten = getKindergarten(id);

        // 시간 변환
        java.time.LocalTime open = parseTime(openTime);
        java.time.LocalTime close = parseTime(closeTime);

        // 수정
        kindergarten.update(name, address, phone, open, close);
    }

    @Transactional
    public void updateKindergartenForRequester(Long id, Long requesterId, String name, String address,
                                               String phone, String openTime, String closeTime) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validatePrincipalSameKindergarten(requester, id);
        updateKindergarten(id, name, address, phone, openTime, closeTime);
    }

    /**
     * 유치원 삭제
     */
    @Transactional
    public void deleteKindergarten(Long id) {
        Kindergarten kindergarten = getKindergarten(id);
        kindergartenRepository.delete(kindergarten);
    }

    @Transactional
    public void deleteKindergartenForRequester(Long id, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validatePrincipalSameKindergarten(requester, id);
        deleteKindergarten(id);
    }

    /**
     * 시간 문자열 파싱 (HH:mm)
     */
    private java.time.LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        return java.time.LocalTime.parse(time);
    }
}
