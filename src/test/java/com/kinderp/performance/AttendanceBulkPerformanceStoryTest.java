package com.kinderp.performance;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.attendance.dto.request.BulkAttendanceRequest;
import com.kinderp.domain.attendance.service.AttendanceService;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("성능 스토리 - 반 일괄 출결 N+1")
@Tag("performance")
class AttendanceBulkPerformanceStoryTest extends BaseIntegrationTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("일괄 출결 쿼리 수는 원생 수와 무관하게 일정하다")
    void bulkUpdate_QueryCountDoesNotGrowWithKidCount() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        addKids(9, 0);
        long small = measureBulk(LocalDate.of(2025, 3, 10), statistics);

        addKids(30, 9);
        long large = measureBulk(LocalDate.of(2025, 3, 11), statistics);

        System.out.printf("[PERF] attendance-bulk reads - kids=10 queries=%d, kids=40 queries=%d%n", small, large);

        // 쓰기(INSERT)는 행마다 나가는 것이 정상이므로 읽기 쿼리만 비교한다. 개선 전에는 원생마다 읽기 3개가 늘었다.
        assertThat(large)
                .as("kids=10 read queries=%d, kids=40 read queries=%d", small, large)
                .isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(8);
    }

    private void addKids(int count, int offset) {
        List<Kid> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(Kid.create(classroom, "성능원생" + (offset + i), LocalDate.of(2020, 1, 1),
                    Gender.MALE, LocalDate.of(2025, 1, 1)));
        }
        kidRepository.saveAll(created);
        entityManager.flush();
        entityManager.clear();
    }

    private long measureBulk(LocalDate date, Statistics statistics) throws Exception {
        BulkAttendanceRequest request = objectMapper.readValue("""
                {"classroomId": %d, "date": "%s", "status": "PRESENT"}
                """.formatted(classroom.getId(), date), BulkAttendanceRequest.class);

        entityManager.clear();
        statistics.clear();
        int updated = attendanceService.bulkUpdateAttendance(request, teacherMember.getId());
        entityManager.flush();
        long queries = statistics.getPrepareStatementCount()
                - statistics.getEntityInsertCount()
                - statistics.getEntityUpdateCount();
        assertThat(updated).isGreaterThanOrEqualTo(10);
        return queries;
    }
}
