package com.kinderp.domain.kidapplication.service;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.service.NotificationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KidApplicationNotificationService {

    private static final String REVIEW_QUEUE_URL = "/applications/pending";
    private static final String PARENT_APPLICATIONS_URL = "/applications/pending";

    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    public void notifyStaffAboutApplication(Kindergarten kindergarten, Member parent, String kidName) {
        String content = parent.getName() + " 학부모님이 자녀(" + kidName + ")의 입학을 신청했습니다.";
        notifyStaffWithLink(kindergarten, NotificationType.KID_APPLICATION_SUBMITTED, "새로운 입학 신청", content);
    }

    public void notifyStaffAboutCancellation(Kindergarten kindergarten, Member parent, String kidName) {
        String content = parent.getName() + " 학부모님이 자녀(" + kidName + ")의 입학 신청을 취소했습니다.";
        notifyStaff(kindergarten, NotificationType.KID_APPLICATION_CANCELLED, "입학 신청 취소", content);
    }

    public void notifyStaffAboutOfferAccepted(Kindergarten kindergarten, Member parent, String kidName) {
        String content = parent.getName() + " 학부모님이 " + kidName + "의 입학 제안을 수락했습니다.";
        notifyStaffWithLink(kindergarten, NotificationType.KID_APPLICATION_OFFER_ACCEPTED, "입학 제안 수락", content);
    }

    public void notifyParentAboutApproval(Member parent, String kidName, Kindergarten kindergarten) {
        notificationService.notifyWithLink(
                parent.getId(),
                NotificationType.KID_APPLICATION_APPROVED,
                "입학 승인",
                kidName + "의 " + kindergarten.getName() + " 입학이 승인되었습니다.",
                PARENT_APPLICATIONS_URL
        );
    }

    public void notifyParentAboutRejection(Member parent, String kidName, Kindergarten kindergarten, String reason) {
        String content = kidName + "의 " + kindergarten.getName() + " 입학이 거절되었습니다.";
        if (reason != null && !reason.isEmpty()) {
            content += "\n사유: " + reason;
        }
        notificationService.notifyWithLink(
                parent.getId(),
                NotificationType.KID_APPLICATION_REJECTED,
                "입학 거절",
                content,
                PARENT_APPLICATIONS_URL
        );
    }

    public void notifyParentAboutWaitlist(Member parent, String kidName, Classroom classroom) {
        notificationService.notifyWithLink(
                parent.getId(),
                NotificationType.KID_APPLICATION_WAITLISTED,
                "입학 대기열 등록",
                kidName + "이(가) " + classroom.getName() + " 대기열에 등록되었습니다.",
                PARENT_APPLICATIONS_URL
        );
    }

    public void notifyParentAboutOffer(Member parent, String kidName, Classroom classroom, LocalDateTime offerExpiresAt) {
        String content = kidName + "에게 " + classroom.getName() + " 입학 제안이 도착했습니다. "
                + "만료 시각: " + offerExpiresAt;
        notificationService.notifyWithLink(
                parent.getId(),
                NotificationType.KID_APPLICATION_OFFERED,
                "입학 제안 도착",
                content,
                PARENT_APPLICATIONS_URL
        );
    }

    public void notifyParentAboutOfferExpired(Member parent, String kidName) {
        notificationService.notifyWithLink(
                parent.getId(),
                NotificationType.KID_APPLICATION_OFFER_EXPIRED,
                "입학 제안 만료",
                kidName + "의 입학 제안이 만료되었습니다.",
                PARENT_APPLICATIONS_URL
        );
    }

    private void notifyStaffWithLink(Kindergarten kindergarten, NotificationType type, String title, String content) {
        memberRepository.findByKindergartenIdAndRole(kindergarten.getId(), MemberRole.PRINCIPAL)
                .ifPresent(principal -> notificationService.notifyWithLink(
                        principal.getId(),
                        type,
                        title,
                        content,
                        REVIEW_QUEUE_URL
                ));

        memberRepository.findAllByKindergartenIdAndRole(kindergarten.getId(), MemberRole.TEACHER)
                .forEach(teacher -> notificationService.notifyWithLink(
                        teacher.getId(),
                        type,
                        title,
                        content,
                        REVIEW_QUEUE_URL
                ));
    }

    private void notifyStaff(Kindergarten kindergarten, NotificationType type, String title, String content) {
        memberRepository.findByKindergartenIdAndRole(kindergarten.getId(), MemberRole.PRINCIPAL)
                .ifPresent(principal -> notificationService.notify(principal.getId(), type, title, content));

        memberRepository.findAllByKindergartenIdAndRole(kindergarten.getId(), MemberRole.TEACHER)
                .forEach(teacher -> notificationService.notify(teacher.getId(), type, title, content));
    }
}
