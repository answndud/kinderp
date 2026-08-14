package com.erp.performance;

import com.erp.common.BaseIntegrationTest;
import com.erp.domain.notepad.entity.Notepad;
import com.erp.domain.notepad.repository.NotepadRepository;
import com.erp.domain.notepad.service.NotepadService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("성능 스토리 - 알림장 N+1")
@Tag("performance")
class NotepadPerformanceStoryTest extends BaseIntegrationTest {

    @Autowired
    private NotepadService notepadService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("읽음 수 조회 경로 - 레거시 대비 쿼리 수/응답시간 비교")
    void compareLegacyVsOptimizedReadCountFlow() {
        prepareNotepads(80);

        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        // warm-up
        legacyLoad(classroom.getId(), pageable);
        notepadService.getClassroomNotepads(classroom.getId(), pageable, teacherMember.getId());

        Measurement legacy = measure(statistics, () -> legacyLoad(classroom.getId(), pageable));
        Measurement optimized = measure(statistics,
                () -> notepadService.getClassroomNotepads(classroom.getId(), pageable, teacherMember.getId()));

        System.out.printf("[PERF] legacy  - queries=%d, elapsedMs=%d%n", legacy.queryCount, legacy.elapsedMs);
        System.out.printf("[PERF] optimized - queries=%d, elapsedMs=%d%n", optimized.queryCount, optimized.elapsedMs);

        assertTrue(optimized.queryCount < legacy.queryCount,
                "optimized path must use fewer queries than legacy path");
    }

    @Test
    @DisplayName("대규모 알림장 fixture에서도 최적화 경로 쿼리 예산은 일정하다")
    void optimizedReadCountFlow_StaysWithinQueryBudgetForLargeFixture() {
        prepareNotepads(1_000);

        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        Measurement optimized = measure(statistics,
                () -> notepadService.getClassroomNotepads(classroom.getId(), pageable, teacherMember.getId()));

        System.out.printf("[PERF] notepad-large-fixture - rows=%d, queries=%d, elapsedMs=%d%n",
                1_001,
                optimized.queryCount,
                optimized.elapsedMs);

        assertTrue(optimized.queryCount <= 5,
                "optimized notepad path should keep a fixed query budget for a large fixture");
    }

    private void prepareNotepads(int additionalCount) {
        List<Notepad> created = new ArrayList<>();
        for (int i = 0; i < additionalCount; i++) {
            Notepad item = Notepad.createClassroomNotepad(
                    classroom,
                    teacherMember,
                    "성능테스트 알림장 " + i,
                    "성능테스트 내용 " + i
            );
            if (i % 2 == 0) {
                item.addReadConfirm(parentMember);
            }
            created.add(item);
        }
        notepadRepository.saveAll(created);
        entityManager.flush();
        entityManager.clear();
    }

    private void legacyLoad(Long classroomId, Pageable pageable) {
        Page<Notepad> page = notepadRepository.findClassroomNotepads(classroomId, pageable);
        page.getContent().forEach(notepad -> notepadRepository.findReadConfirmsByNotepadId(notepad.getId()).size());
    }

    private Measurement measure(Statistics statistics, Runnable action) {
        entityManager.clear();
        statistics.clear();
        long start = System.nanoTime();
        action.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long queryCount = statistics.getPrepareStatementCount();
        return new Measurement(queryCount, elapsedMs);
    }

    private record Measurement(long queryCount, long elapsedMs) {
    }
}
