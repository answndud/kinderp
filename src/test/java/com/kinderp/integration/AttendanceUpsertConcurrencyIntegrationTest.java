package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 같은 원생·날짜 출결을 동시에 upsert해도 서버 오류(500) 없이 한 행만 남는지 검증한다.
 * 먼저 insert한 요청이 이기고 늦은 요청은 갱신(200)되거나 유니크 충돌(409)로 응답해야 한다.
 */
@DisplayName("출결 upsert 동시성 통합 테스트")
@Tag("integration")
class AttendanceUpsertConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private KidRepository kidRepository;

    @Test
    @DisplayName("같은 원생·날짜를 동시에 upsert하면 500 없이 출결이 한 건만 남는다")
    void concurrentUpsert_NoServerError_SingleRow() throws Exception {
        long[] ids = writeCommitted(() -> {
            Kindergarten kindergarten = testData.createKindergarten();
            Member teacher = testData.createTestMember(
                    "upsert-teacher@test.com", "동시성교사", MemberRole.TEACHER, "test1234");
            teacher.assignKindergarten(kindergarten);
            memberRepository.save(teacher);
            Classroom classroom = classroomRepository.save(Classroom.create(kindergarten, "동시성반", "5세", 20));
            classroom.assignTeacher(teacher);
            classroomRepository.save(classroom);
            Kid kid = kidRepository.save(Kid.create(
                    classroom, "동시성원생", LocalDate.of(2020, 1, 1), Gender.MALE, LocalDate.of(2025, 1, 1)));
            return new long[]{teacher.getId(), kid.getId()};
        });
        Member teacher = readCommitted(() -> memberRepository.findById(ids[0]).orElseThrow());
        String body = """
                {"kidId": %d, "date": "2025-03-10", "status": "PRESENT"}
                """.formatted(ids[1]);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return mockMvc.perform(post("/api/v1/attendance/upsert")
                                    .with(authenticated(teacher))
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).as("statuses=" + statuses).isNotEmpty().contains(200);
            assertThat(statuses).as("statuses=" + statuses).allMatch(status -> status == 200 || status == 409);
        } finally {
            executor.shutdownNow();
        }

        Integer rows = readCommitted(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE kid_id = ? AND date = ?", Integer.class, ids[1], LocalDate.of(2025, 3, 10)));
        assertThat(rows).isEqualTo(1);
    }
}
