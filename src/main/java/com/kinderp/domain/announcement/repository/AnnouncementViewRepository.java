package com.kinderp.domain.announcement.repository;

import com.kinderp.domain.announcement.entity.AnnouncementView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 공지사항 고유 열람 이력 리포지토리
 */
@Repository
public interface AnnouncementViewRepository extends JpaRepository<AnnouncementView, Long> {

    boolean existsByAnnouncementIdAndViewerId(Long announcementId, Long viewerId);
}
