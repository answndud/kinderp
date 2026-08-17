package com.kinderp.domain.announcement.service;

import com.kinderp.domain.announcement.dto.request.AnnouncementRequest;
import com.kinderp.domain.announcement.dto.response.AnnouncementResponse;
import com.kinderp.domain.announcement.entity.Announcement;
import com.kinderp.domain.announcement.repository.AnnouncementRepository;
import com.kinderp.domain.announcement.repository.AnnouncementViewRepository;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import com.kinderp.domain.domainaudit.service.DomainAuditLogService;
import com.kinderp.domain.kindergarten.service.KindergartenService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.common.PageRequests;
import com.kinderp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공지사항 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementViewRepository announcementViewRepository;
    private final KindergartenService kindergartenService;
    private final MemberService memberService;
    private final NotificationService notificationService;
    private final DashboardService dashboardService;
    private final AccessPolicyService accessPolicyService;
    private final DomainAuditLogService domainAuditLogService;

    /**
     * 공지사항 생성
     */
    @Transactional
    public Long createAnnouncement(AnnouncementRequest request, Long writerId) {
        // 유치원 조회
        var kindergarten = kindergartenService.getKindergarten(request.getKindergartenId());
        // 작성자 조회
        Member writer = memberService.getMemberById(writerId);

        // 작성자 역할 확인 (원장 또는 교사)
        validateWriterRole(writer);
        validateSameKindergarten(writer, kindergarten.getId());

        // 공지사항 생성
        Announcement announcement;
        if (Boolean.TRUE.equals(request.getIsImportant())) {
            announcement = Announcement.createImportant(kindergarten, writer, request.getTitle(), request.getContent());
        } else {
            announcement = Announcement.create(kindergarten, writer, request.getTitle(), request.getContent());
        }

        Announcement saved = announcementRepository.save(announcement);
        evictDashboardStatistics(saved);

        List<MemberRole> targetRoles = resolveTargetRoles(request.getTargetRoles());
        List<Member> receivers = memberService.getMembersByKindergartenAndRoles(
                kindergarten.getId(),
                targetRoles
        );
        if (!receivers.isEmpty()) {
            String title = "새 공지사항: " + saved.getTitle();
            String content = saved.getContent();
            List<Long> receiverIds = new java.util.ArrayList<>();
            for (Member receiver : receivers) {
                receiverIds.add(receiver.getId());
            }
            notificationService.notifyWithLink(receiverIds,
                    NotificationType.ANNOUNCEMENT_CREATED,
                    title,
                    content,
                    "/announcements"
            );
        }

        return saved.getId();
    }

    @Transactional
    public Announcement getAnnouncement(Long id, Long requesterId) {
        Announcement announcement = announcementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateAnnouncementReadAccess(requester, announcement);

        recordView(announcement, requester);
        evictDashboardStatistics(announcement);
        return announcement;
    }

    public Announcement getAnnouncementWithoutIncrement(Long id, Long requesterId) {
        Announcement announcement = findAnnouncementWithRelations(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateAnnouncementReadAccess(requester, announcement);
        return announcement;
    }

    public java.util.List<Announcement> getAnnouncementsByKindergartenForView(Long kindergartenId, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return announcementRepository.findByKindergartenIdWithRelations(kindergartenId);
    }

    public Page<Announcement> getAnnouncementsByKindergarten(Long kindergartenId, int page, int size, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        Pageable pageable = PageRequests.of(page, size, 10, Sort.by("isImportant").descending()
                .and(Sort.by("createdAt").descending()));
        return announcementRepository.findByKindergartenIdAndDeletedAtIsNull(kindergartenId, pageable);
    }

    public Page<Announcement> getImportantAnnouncements(Long kindergartenId, int page, int size, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        Pageable pageable = PageRequests.of(page, size, 10, Sort.by("createdAt").descending());
        return announcementRepository.findImportantByKindergartenId(kindergartenId, pageable);
    }

    public Page<Announcement> searchByTitle(Long kindergartenId, String title, int page, int size, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        Pageable pageable = PageRequests.of(page, size, 10, Sort.by("createdAt").descending());
        return announcementRepository.findByKindergartenIdAndTitleContaining(kindergartenId, title, pageable);
    }

    public Page<Announcement> getMostViewedAnnouncements(Long kindergartenId, int page, int size, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        Pageable pageable = PageRequests.of(page, size, 10, Sort.unsorted());
        return announcementRepository.findMostViewedByKindergartenId(kindergartenId, pageable);
    }

    /**
     * 공지사항 수정
     */
    @Transactional
    public void updateAnnouncement(Long id, AnnouncementRequest request, Long requesterId) {
        Announcement announcement = findAnnouncementWithRelations(id);

        // 수정 권한 확인 (원장 또는 교사만 가능)
        Member requester = memberService.getMemberById(requesterId);
        validateWriterRole(requester);
        validateSameKindergarten(requester, announcement.getKindergarten().getId());

        announcement.update(request.getTitle(), request.getContent());

        // 중요 공지 설정 변경
        if (request.getIsImportant() != null) {
            announcement.setImportant(request.getIsImportant());
        }
        evictDashboardStatistics(announcement);
        domainAuditLogService.record(
                requester,
                announcement.getKindergarten().getId(),
                DomainAuditAction.ANNOUNCEMENT_UPDATED,
                DomainAuditTargetType.ANNOUNCEMENT,
                announcement.getId(),
                requester.getName() + "이(가) 공지사항 '" + announcement.getTitle() + "'을(를) 수정했습니다.",
                java.util.Map.of("important", announcement.isImportant())
        );
    }

    /**
     * 공지사항 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteAnnouncement(Long id, Long requesterId) {
        Announcement announcement = findAnnouncementWithRelations(id);

        // 삭제 권한 확인 (원장 또는 교사만 가능)
        Member requester = memberService.getMemberById(requesterId);
        validateWriterRole(requester);
        validateSameKindergarten(requester, announcement.getKindergarten().getId());

        announcement.softDelete();
        evictDashboardStatistics(announcement);
        domainAuditLogService.record(
                requester,
                announcement.getKindergarten().getId(),
                DomainAuditAction.ANNOUNCEMENT_DELETED,
                DomainAuditTargetType.ANNOUNCEMENT,
                announcement.getId(),
                requester.getName() + "이(가) 공지사항 '" + announcement.getTitle() + "'을(를) 삭제했습니다.",
                java.util.Map.of("important", announcement.isImportant())
        );
    }

    /**
     * 중요 공지 토글
     */
    @Transactional
    public void toggleImportant(Long id, Long requesterId) {
        Announcement announcement = findAnnouncementWithRelations(id);

        // 토글 권한 확인 (원장 또는 교사만 가능)
        Member requester = memberService.getMemberById(requesterId);
        validateWriterRole(requester);
        validateSameKindergarten(requester, announcement.getKindergarten().getId());

        announcement.toggleImportant();
        evictDashboardStatistics(announcement);
    }

    /**
     * 작성자 역할 확인 (원장 또는 교사)
     */
    private void validateWriterRole(Member writer) {
        if (writer.getRole() != com.kinderp.domain.member.entity.MemberRole.TEACHER &&
            writer.getRole() != com.kinderp.domain.member.entity.MemberRole.PRINCIPAL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateSameKindergarten(Member member, Long targetKindergartenId) {
        if (member.getKindergarten() == null || !member.getKindergarten().getId().equals(targetKindergartenId)) {
            throw new BusinessException(ErrorCode.KINDERGARTEN_ACCESS_DENIED);
        }
    }

    /**
     * Announcement Response 변환
     */
    public AnnouncementResponse toResponse(Announcement announcement) {
        return AnnouncementResponse.from(announcement);
    }

    private List<MemberRole> resolveTargetRoles(List<MemberRole> targetRoles) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return List.of(MemberRole.PRINCIPAL, MemberRole.TEACHER, MemberRole.PARENT);
        }
        return targetRoles;
    }

    private void evictDashboardStatistics(Announcement announcement) {
        dashboardService.evictDashboardStatisticsCache(announcement.getKindergarten().getId());
    }

    private Announcement findAnnouncementWithRelations(Long id) {
        return announcementRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }

    private void recordView(Announcement announcement, Member requester) {
        announcement.incrementViewCount();

        if (announcementViewRepository.existsByAnnouncementIdAndViewerId(announcement.getId(), requester.getId())) {
            return;
        }

        try {
            announcementViewRepository.save(com.kinderp.domain.announcement.entity.AnnouncementView.create(announcement, requester));
        } catch (DataIntegrityViolationException ignored) {
            // 고유 열람은 1회만 집계한다.
        }
    }
}
