package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.dashboard.service.DashboardService;
import com.kinderp.domain.kid.dto.request.KidRequest;
import com.kinderp.domain.kid.service.KidService;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드 통계 캐시가 커밋 이후에 비워지고, 통계에 영향을 주는 변경마다 비워지는지 검증한다.
 * 이벤트는 커밋 뒤에 처리되므로 테스트 트랜잭션 대신 writeCommitted로 커밋한다.
 */
@DisplayName("대시보드 통계 캐시 무효화 통합 테스트")
@Tag("integration")
class DashboardCacheEvictionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private KidService kidService;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long kindergartenId;
    private Long classroomId;
    private Long principalId;

    @BeforeEach
    void createCommittedFixture() {
        cacheManager.getCache("dashboardStatistics").clear();
        Long[] ids = writeCommitted(() -> {
            Kindergarten created = testData.createKindergarten();
            Member principal = testData.createTestMember(
                    "cache-principal@test.com", "캐시원장", MemberRole.PRINCIPAL, "test1234");
            principal.assignKindergarten(created);
            memberRepository.save(principal);
            Classroom classroom = classroomRepository.save(Classroom.create(created, "캐시반", "5세", 20));
            return new Long[]{created.getId(), classroom.getId(), principal.getId()};
        });
        kindergartenId = ids[0];
        classroomId = ids[1];
        principalId = ids[2];
    }

    @Test
    @DisplayName("커밋되기 전에는 캐시를 비우지 않고, 커밋 뒤에 비운다")
    void evict_HappensAfterCommit() {
        warmCache();
        assertThat(cacheManager.getCache("dashboardStatistics").get(kindergartenId)).isNotNull();

        writeCommitted(() -> {
            dashboardService.evictDashboardStatisticsCache(kindergartenId);
            // 아직 커밋 전이므로 다른 요청이 옛 값을 읽어도 이 시점에는 캐시가 그대로여야 한다.
            assertThat(cacheManager.getCache("dashboardStatistics").get(kindergartenId)).isNotNull();
            return null;
        });

        assertThat(cacheManager.getCache("dashboardStatistics").get(kindergartenId)).isNull();
    }

    @Test
    @DisplayName("롤백된 트랜잭션은 캐시를 비우지 않는다")
    void evict_SkippedOnRollback() {
        warmCache();

        try {
            writeCommitted(() -> {
                dashboardService.evictDashboardStatisticsCache(kindergartenId);
                throw new IllegalStateException("rollback");
            });
        } catch (IllegalStateException expected) {
            // 롤백 확인용
        }

        assertThat(cacheManager.getCache("dashboardStatistics").get(kindergartenId)).isNotNull();
    }

    @Test
    @DisplayName("원생을 등록·삭제하면 통계의 총 원생 수가 바로 반영된다")
    void kidCreateAndDelete_RefreshesStatistics() throws Exception {
        assertThat(totalKids()).isZero();

        KidRequest request = objectMapper.readValue("""
                {"classroomId": %d, "name": "캐시원생", "birthDate": "2020-01-01",
                 "gender": "MALE", "admissionDate": "2020-02-01"}
                """.formatted(classroomId), KidRequest.class);
        Long kidId = writeCommitted(() -> kidService.createKid(request, principalId));
        assertThat(totalKids()).isEqualTo(1);

        writeCommitted(() -> {
            kidService.deleteKid(kidId, principalId);
            return null;
        });
        assertThat(totalKids()).isZero();
    }

    private void warmCache() {
        totalKids();
    }

    private int totalKids() {
        return readCommitted(() -> dashboardService.getDashboardStatistics(
                kindergartenRepository.findById(kindergartenId).orElseThrow())).totalKids();
    }
}
