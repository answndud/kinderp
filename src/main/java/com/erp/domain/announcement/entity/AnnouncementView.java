package com.erp.domain.announcement.entity;

import com.erp.domain.member.entity.Member;
import com.erp.global.common.ProductTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공지사항 고유 열람 이력 엔티티
 */
@Entity
@Table(
        name = "announcement_view",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_announcement_view_announcement_viewer",
                        columnNames = {"announcement_id", "viewer_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", nullable = false)
    private Member viewer;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    public static AnnouncementView create(Announcement announcement, Member viewer) {
        AnnouncementView announcementView = new AnnouncementView();
        announcementView.announcement = announcement;
        announcementView.viewer = viewer;
        announcementView.readAt = ProductTime.nowDateTime();
        return announcementView;
    }
}
