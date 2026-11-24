package com.kinderp.integration;

import com.kinderp.common.BaseIntegrationTest;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.kidapplication.dto.request.AcceptKidApplicationOfferRequest;
import com.kinderp.domain.kidapplication.dto.request.ApproveKidApplicationRequest;
import com.kinderp.domain.kidapplication.entity.ApplicationStatus;
import com.kinderp.domain.kidapplication.entity.KidApplication;
import com.kinderp.domain.kidapplication.repository.KidApplicationRepository;
import com.kinderp.domain.kidapplication.service.KidApplicationNotificationService;
import com.kinderp.domain.kidapplication.service.KidApplicationService;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.common.ProductTime;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 입학 신청 승인·offer 수락·만료가 동시에 일어나도 정원과 상태가 깨지지 않는지 검증한다.
 * BaseIntegrationTest의 setUp 데이터는 테스트 트랜잭션에 묶여 다른 스레드에서 보이지 않으므로
 * 이 클래스는 모든 픽스처를 writeCommitted로 따로 커밋한다.
 */
@DisplayName("입학 신청 동시성 통합 테스트")
@Tag("integration")
class KidApplicationConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KidApplicationService kidApplicationService;

    @Autowired
    private KidApplicationRepository kidApplicationRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private KidRepository kidRepository;

    @MockitoSpyBean
    private KidApplicationNotificationService notificationService;

    @Test
    @DisplayName("정원이 1자리 남은 반에 두 신청을 동시에 승인하면 정확히 한 건만 승인된다")
    void concurrentApprove_OnlyOneSeatIsGranted() throws Exception {
        Fixture fixture = createFixture("approve", 1);
        Long firstId = createPendingApplication(fixture, "approve-parent-1@test.com", "첫째아이");
        Long secondId = createPendingApplication(fixture, "approve-parent-2@test.com", "둘째아이");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> results = new ArrayList<>();
        try {
            for (Long applicationId : List.of(firstId, secondId)) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        kidApplicationService.approve(
                                applicationId,
                                new ApproveKidApplicationRequest(fixture.classroomId(), null),
                                fixture.principalId());
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ErrorCode> failures = new ArrayList<>();
            int success = 0;
            for (Future<ErrorCode> result : results) {
                ErrorCode failure = result.get(30, TimeUnit.SECONDS);
                if (failure == null) {
                    success++;
                } else {
                    failures.add(failure);
                }
            }

            assertThat(success).as("failures=" + failures).isEqualTo(1);
            assertThat(failures).containsExactly(ErrorCode.CLASSROOM_CAPACITY_EXCEEDED);
        } finally {
            executor.shutdownNow();
        }

        assertThat(readCommitted(() -> kidRepository.countByClassroomIdAndDeletedAtIsNull(fixture.classroomId())))
                .isEqualTo(1L);
        List<ApplicationStatus> statuses = readCommitted(() -> List.of(
                kidApplicationRepository.findById(firstId).orElseThrow().getStatus(),
                kidApplicationRepository.findById(secondId).orElseThrow().getStatus()));
        assertThat(statuses).containsExactlyInAnyOrder(ApplicationStatus.APPROVED, ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("학부모가 offer를 수락하는 중에 만료 스케줄러가 돌아도 수락 결과를 덮어쓰지 않는다")
    void expireOffers_DoesNotOverwriteConcurrentAcceptance() throws Exception {
        Fixture fixture = createFixture("expire", 5);
        LocalDateTime expiresAt = ProductTime.nowDateTime().plusSeconds(3);
        Member parent = writeCommitted(() -> testData.createTestMember(
                "expire-parent@test.com", "만료학부모", MemberRole.PARENT, "test1234"));
        Long applicationId = writeCommitted(() -> {
            Kindergarten kindergarten = kindergartenRepository.findById(fixture.kindergartenId()).orElseThrow();
            Classroom classroom = classroomRepository.findById(fixture.classroomId()).orElseThrow();
            Member principal = memberRepository.findById(fixture.principalId()).orElseThrow();
            KidApplication application = KidApplication.create(
                    memberRepository.findById(parent.getId()).orElseThrow(), kindergarten,
                    "수락아이", LocalDate.of(2020, 5, 5), Gender.FEMALE, classroom, null);
            application.offerSeat(classroom, principal, expiresAt, "offer");
            return kidApplicationRepository.save(application).getId();
        });

        // 수락 트랜잭션이 신청 행을 잠근 채 알림 단계에서 멈추도록 만든다.
        CountDownLatch insideAccept = new CountDownLatch(1);
        CountDownLatch releaseAccept = new CountDownLatch(1);
        doAnswer(invocation -> {
            insideAccept.countDown();
            assertThat(releaseAccept.await(30, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(notificationService).notifyParentAboutApproval(any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> accept = executor.submit(() -> kidApplicationService.acceptOffer(
                    applicationId, new AcceptKidApplicationOfferRequest(null), parent.getId()));
            assertThat(insideAccept.await(15, TimeUnit.SECONDS)).isTrue();

            // offer 만료 시각이 지나도록 기다린 뒤, 수락이 아직 커밋되지 않은 상태에서 스케줄러를 실행한다.
            long waitMillis = java.time.Duration.between(ProductTime.nowDateTime(), expiresAt).toMillis() + 500;
            if (waitMillis > 0) {
                Thread.sleep(waitMillis);
            }
            Future<?> expire = executor.submit(kidApplicationService::expireOffers);
            Thread.sleep(1000);

            releaseAccept.countDown();
            accept.get(30, TimeUnit.SECONDS);
            expire.get(30, TimeUnit.SECONDS);
        } finally {
            releaseAccept.countDown();
            executor.shutdownNow();
        }

        KidApplication result = readCommitted(() -> kidApplicationRepository.findById(applicationId).orElseThrow());
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(result.getKidId()).isNotNull();
        assertThat(readCommitted(() -> kidRepository.countByClassroomIdAndDeletedAtIsNull(fixture.classroomId())))
                .isEqualTo(1L);
        verify(notificationService, never()).notifyParentAboutOfferExpired(any(), any());
    }

    @Test
    @DisplayName("만료 시각이 지난 offer는 만료 처리되고 한 번만 알림이 간다")
    void expireOffers_ExpiresOverdueOfferOnce() throws Exception {
        Fixture fixture = createFixture("overdue", 5);
        Long applicationId = writeCommitted(() -> {
            Member parent = testData.createTestMember(
                    "overdue-parent@test.com", "지난학부모", MemberRole.PARENT, "test1234");
            Kindergarten kindergarten = kindergartenRepository.findById(fixture.kindergartenId()).orElseThrow();
            Classroom classroom = classroomRepository.findById(fixture.classroomId()).orElseThrow();
            Member principal = memberRepository.findById(fixture.principalId()).orElseThrow();
            KidApplication application = KidApplication.create(
                    parent, kindergarten, "지난아이", LocalDate.of(2020, 6, 6), Gender.MALE, classroom, null);
            application.offerSeat(classroom, principal, ProductTime.nowDateTime().minusMinutes(1), "offer");
            return kidApplicationRepository.save(application).getId();
        });

        // 테스트 스레드의 트랜잭션 스냅샷에는 방금 커밋한 픽스처가 보이지 않으므로 별도 스레드에서 실행한다.
        runInOtherThread(kidApplicationService::expireOffers);
        runInOtherThread(kidApplicationService::expireOffers);

        assertThat(readCommitted(() -> kidApplicationRepository.findById(applicationId).orElseThrow()).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_EXPIRED);
        verify(notificationService, org.mockito.Mockito.times(1)).notifyParentAboutOfferExpired(any(), any());
    }

    private void runInOtherThread(Runnable task) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(task).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createFixture(String prefix, int capacity) {
        return writeCommitted(() -> {
            Kindergarten kindergarten = testData.createKindergarten();
            Member principal = testData.createTestMember(
                    prefix + "-principal@test.com", "동시성원장", MemberRole.PRINCIPAL, "test1234");
            principal.assignKindergarten(kindergarten);
            memberRepository.save(principal);
            Classroom classroom = classroomRepository.save(Classroom.create(kindergarten, "정원반", "5세", capacity));
            return new Fixture(kindergarten.getId(), classroom.getId(), principal.getId());
        });
    }

    private Long createPendingApplication(Fixture fixture, String parentEmail, String kidName) {
        return writeCommitted(() -> {
            Member parent = testData.createTestMember(parentEmail, "신청학부모", MemberRole.PARENT, "test1234");
            Kindergarten kindergarten = kindergartenRepository.findById(fixture.kindergartenId()).orElseThrow();
            Classroom classroom = classroomRepository.findById(fixture.classroomId()).orElseThrow();
            KidApplication application = KidApplication.create(
                    parent, kindergarten, kidName, LocalDate.of(2020, 3, 3), Gender.MALE, classroom, null);
            return kidApplicationRepository.save(application).getId();
        });
    }

    private record Fixture(Long kindergartenId, Long classroomId, Long principalId) {
    }
}
