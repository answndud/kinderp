package com.erp.global.config;

import com.erp.domain.announcement.entity.Announcement;
import com.erp.domain.announcement.repository.AnnouncementRepository;
import com.erp.domain.attendance.entity.Attendance;
import com.erp.domain.attendance.entity.AttendanceStatus;
import com.erp.domain.attendance.repository.AttendanceRepository;
import com.erp.domain.authaudit.entity.AuthAuditEventType;
import com.erp.domain.authaudit.entity.AuthAuditLog;
import com.erp.domain.authaudit.entity.AuthAuditResult;
import com.erp.domain.authaudit.repository.AuthAuditLogRepository;
import com.erp.domain.calendar.entity.CalendarEvent;
import com.erp.domain.calendar.entity.CalendarEventType;
import com.erp.domain.calendar.entity.CalendarScopeType;
import com.erp.domain.calendar.entity.RepeatType;
import com.erp.domain.calendar.repository.CalendarEventRepository;
import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.classroom.repository.ClassroomRepository;
import com.erp.domain.domainaudit.entity.DomainAuditAction;
import com.erp.domain.domainaudit.entity.DomainAuditLog;
import com.erp.domain.domainaudit.entity.DomainAuditTargetType;
import com.erp.domain.domainaudit.repository.DomainAuditLogRepository;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.kindergarten.repository.KindergartenRepository;
import com.erp.domain.kid.entity.Gender;
import com.erp.domain.kid.entity.Kid;
import com.erp.domain.kid.entity.ParentKid;
import com.erp.domain.kid.entity.Relationship;
import com.erp.domain.kid.repository.KidRepository;
import com.erp.domain.kid.repository.ParentKidRepository;
import com.erp.domain.kidapplication.entity.KidApplication;
import com.erp.domain.kidapplication.repository.KidApplicationRepository;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberAuthProvider;
import com.erp.domain.member.entity.MemberRole;
import com.erp.domain.member.entity.MemberStatus;
import com.erp.domain.member.repository.MemberRepository;
import com.erp.domain.notepad.entity.Notepad;
import com.erp.domain.notepad.repository.NotepadRepository;
import com.erp.domain.notification.entity.Notification;
import com.erp.domain.notification.entity.NotificationDeliveryStatus;
import com.erp.domain.notification.entity.NotificationOutbox;
import com.erp.domain.notification.entity.NotificationType;
import com.erp.domain.notification.repository.NotificationOutboxRepository;
import com.erp.domain.notification.repository.NotificationRepository;
import com.erp.global.common.ProductTime;
import com.erp.domain.notification.entity.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 로컬 개발용 더미 데이터 로더
 *
 * 테스트 계정 정보 (비밀번호: test1234!):
 * - 원장A: principal@test.com / test1234!
 * - 원장B: principal2@test.com / test1234!
 * - 선생A1: teacher1@test.com / test1234!
 * - 선생A2: teacher2@test.com / test1234!
 * - 선생B1: teacher3@test.com / test1234!
 * - 선생B2: teacher4@test.com / test1234!
 * - 학부모A1-3: parent{1,2,3}@test.com / test1234!
 * - 학부모B1-3: parent{4,5,6}@test.com / test1234!
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private static final String SEED_PRINCIPAL_A_EMAIL = "principal@test.com";
    private static final String SEED_PRINCIPAL_B_EMAIL = "principal2@test.com";

    private final PasswordEncoder passwordEncoder;
    private final KindergartenRepository kindergartenRepository;
    private final MemberRepository memberRepository;
    private final ClassroomRepository classroomRepository;
    private final KidRepository kidRepository;
    private final ParentKidRepository parentKidRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotepadRepository notepadRepository;
    private final AnnouncementRepository announcementRepository;
    private final AuthAuditLogRepository authAuditLogRepository;
    private final DomainAuditLogRepository domainAuditLogRepository;
    private final KidApplicationRepository kidApplicationRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final SeedProperties seedProperties;
    private final Random random = new Random();

    // 테스트용 고정 비밀번호
    private static final String TEST_PASSWORD = "test1234!";

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 시드 계정이 이미 있으면 중복 생성 방지
        if (memberRepository.existsByEmail(SEED_PRINCIPAL_A_EMAIL)
                || memberRepository.existsByEmail(SEED_PRINCIPAL_B_EMAIL)) {
            log.info("Seed principals already exist. Checking demo scenario sample supplements.");
            supplementDemoScenarioSamplesIfPossible();
            if (seedProperties.isLogCredentials()) {
                log.info("Seed credentials are enabled for logging in this environment.");
            }
            return;
        }

        log.info("Loading dummy data...");

        // 1. 유치원 2개 생성
        Kindergarten kgA = createKindergarten("해바라기 유치원", "서울시 강남구 테헤란로 123", "02-1234-5678");
        Kindergarten kgB = createKindergarten("꿈나무 유치원", "서울시 서초구 강남대로 456", "02-9876-5432");

        // 2. 원장 2명 생성 (각 유치원 1명씩)
        Member principalA = createMember("principal@test.com", "김원장", MemberRole.PRINCIPAL, kgA);
        Member principalB = createMember("principal2@test.com", "이원장", MemberRole.PRINCIPAL, kgB);

        // 3. 선생님 4명 생성 (A: 2명, B: 2명)
        Member teacherA1 = createMember("teacher1@test.com", "김교사", MemberRole.TEACHER, kgA);
        Member teacherA2 = createMember("teacher2@test.com", "박교사", MemberRole.TEACHER, kgA);
        Member teacherB1 = createMember("teacher3@test.com", "최교사", MemberRole.TEACHER, kgB);
        Member teacherB2 = createMember("teacher4@test.com", "정교사", MemberRole.TEACHER, kgB);

        // 4. 반 4개 생성 (각 유치원 2개씩)
        Classroom classA1 = createClassroom(kgA, "해바라기반", "5세", teacherA1);
        Classroom classA2 = createClassroom(kgA, "장미반", "6세", teacherA2);
        Classroom classB1 = createClassroom(kgB, "나무반", "5세", teacherB1);
        Classroom classB2 = createClassroom(kgB, "꽃반", "6세", teacherB2);

        // 5. 원아 생성 (A: 6명, B: 6명 = 총 12명)
        List<Kid> kidsA = new ArrayList<>();
        kidsA.addAll(createKidsForClassroom(classA1, "준우", "서윤", "도윤"));
        kidsA.addAll(createKidsForClassroom(classA2, "시우", "하은", "지호"));

        List<Kid> kidsB = new ArrayList<>();
        kidsB.addAll(createKidsForClassroom(classB1, "주원", "수빈", "지원"));
        kidsB.addAll(createKidsForClassroom(classB2, "다은", "예준", "연우"));

        // 6. 학부모 생성 (A: 3명, B: 3명 = 총 6명)
        // A 유치원 학부모 3명 (각각 여러 자녀 연결)
        Member parentA1 = createMember("parent1@test.com", "준우아빠", MemberRole.PARENT, kgA);
        Member parentA2 = createMember("parent2@test.com", "서윤엄마", MemberRole.PARENT, kgA);
        Member parentA3 = createMember("parent3@test.com", "시우할아빠", MemberRole.PARENT, kgA);

        // A 유치원 자녀들과 부모 연결
        createParentKid(parentA1, kidsA.get(0), Relationship.FATHER);  // 준우
        createParentKid(parentA1, kidsA.get(3), Relationship.FATHER);  // 시우
        createParentKid(parentA2, kidsA.get(1), Relationship.MOTHER);  // 서윤
        createParentKid(parentA2, kidsA.get(4), Relationship.MOTHER);  // 하은
        createParentKid(parentA3, kidsA.get(2), Relationship.FATHER);  // 도윤
        createParentKid(parentA3, kidsA.get(5), Relationship.FATHER);  // 지호

        // B 유치원 학부모 3명
        Member parentB1 = createMember("parent4@test.com", "주원엄마", MemberRole.PARENT, kgB);
        Member parentB2 = createMember("parent5@test.com", "수빈아빠", MemberRole.PARENT, kgB);
        Member parentB3 = createMember("parent6@test.com", "지원할머니", MemberRole.PARENT, kgB);

        // B 유치원 자녀들과 부모 연결
        createParentKid(parentB1, kidsB.get(0), Relationship.MOTHER);  // 주원
        createParentKid(parentB1, kidsB.get(3), Relationship.MOTHER);  // 다은
        createParentKid(parentB2, kidsB.get(1), Relationship.FATHER);  // 수빈
        createParentKid(parentB2, kidsB.get(4), Relationship.FATHER);  // 예준
        createParentKid(parentB3, kidsB.get(2), Relationship.GRANDMOTHER);  // 지원
        createParentKid(parentB3, kidsB.get(5), Relationship.GRANDMOTHER);  // 연우

        // 7. 출석부 생성 (최근 7일간)
        LocalDate today = ProductTime.today();
        for (Kid kid : kidsA) {
            for (int i = 0; i < 7; i++) {
                LocalDate date = today.minusDays(i);
                createAttendance(kid, date);
            }
        }
        for (Kid kid : kidsB) {
            for (int i = 0; i < 7; i++) {
                LocalDate date = today.minusDays(i);
                createAttendance(kid, date);
            }
        }

        // 8. 알림장 생성
        createNotepad(classA1, teacherA1, "오늘의 활동 안내", "오늘은 미술 시간에 그림 그리기 활동을 했습니다. 아이들이 참 재미있어하네요!", null);
        createNotepad(classA1, teacherA1, "주간 식단 안내", "이번 주 월요일: 김밥, 화요일: 비빔밥, 수요목: 돈가스, 금요일: 떡국", null);
        createNotepad(classA2, teacherA2, "현장 학습 안내", "다음 주 화요일은 과학관으로 현장 학습을 갑니다. 간편한 복장으로 와주세요.", null);
        createNotepad(classA2, teacherA2, "날씨에 따른 준비물", "내일은 비가 온다고 하니 우산을 꼭 챙겨주세요.", null);
        createNotepad(classB1, teacherB1, "체육 대회 연습", "이번 주부터 체육 대회 연습을 시작합니다. 운동화를 꼭 신어주세요.", null);
        createNotepad(classB1, teacherB1, "도서관 이용 안내", "매주 수요일은 도서관 날입니다. 도서 대출증을 챙겨주세요.", null);
        createNotepad(classB2, teacherB2, "음악 발표회", "이번 달 말에 음악 발표회가 있습니다. 악기 연습을 열심히 해주세요.", null);
        createNotepad(classB2, teacherB2, "비 오는 날 실내 놀이", "비가 오는 날은 실내에서 보드 게임과 블록 놀이를 합니다.", null);

        // 원아별 알림장
        createNotepad(classA1, teacherA1, "준우 생일 축하", "오늘 준우의 5번째 생일을 축하합니다!", kidsA.get(0));
        createNotepad(classA2, teacherA2, "시우 칭찬 일기", "시우가 친구들과 사이좋게 지내는 모습이 아주 좋습니다.", kidsA.get(3));

        // 9. 공지사항 생성
        createAnnouncement(kgA, principalA, "[긴급] 송파구 코로나19 확진자 동선 안내", "송파구에 코로나19 확진자 동선이 발생하여 이를 안내드립니다.", true);
        createAnnouncement(kgA, principalA, "5월 가정 통신문 발송 안내", "5월 가정 통신문을 오늘 발송하였습니다. 확인 부탁드립니다.", false);
        createAnnouncement(kgA, principalA, "여름 방학 일정 안내", "올해 여름 방학은 7월 20일부터 8월 20일까지입니다.", false);
        createAnnouncement(kgB, principalB, "[중요] 어린이잔치 행사 안내", "다음 주 5일 어린이날을 맞아 특별 행사가 준비되었습니다.", true);
        createAnnouncement(kgB, principalB, "새 학기 입학 안내", "2025학년도 새 학기 입학 원서 접수가 시작되었습니다.", false);
        createAnnouncement(kgB, principalB, "급식비 납부 안내", "이번 달 급식비를 5월 10일까지 납부부탁드립니다.", false);

        // 10. 인증 감사 로그 시드 생성 (데모/로컬 운영 콘솔 확인용)
        createAuthAuditLog(principalA, AuthAuditEventType.LOGIN, AuthAuditResult.SUCCESS, MemberAuthProvider.LOCAL, null, "198.51.100.10");
        createAuthAuditLog(principalA, AuthAuditEventType.REFRESH, AuthAuditResult.SUCCESS, null, null, "198.51.100.10");
        createAuthAuditLog(teacherA1, AuthAuditEventType.SOCIAL_LINK, AuthAuditResult.SUCCESS, MemberAuthProvider.GOOGLE, null, "198.51.100.31");
        createAuthAuditLog(parentA1, AuthAuditEventType.SOCIAL_UNLINK, AuthAuditResult.FAILURE, MemberAuthProvider.KAKAO, "A010", "198.51.100.41");
        createAuthAuditLog(parentA2, AuthAuditEventType.LOGIN, AuthAuditResult.SUCCESS, MemberAuthProvider.LOCAL, null, "203.0.113.55");

        // 11. 입학 신청 workflow 시드 생성 (검토 큐/면접 시연용)
        KidApplication pendingApplication = createPendingApplication(parentA1, kgA, classA1, "민준", "신규 입학 상담 후 서류 대기");
        KidApplication waitlistedApplication = createWaitlistedApplication(parentA2, kgA, classA1, teacherA1, "유나", "해바라기반 정원 대기로 waitlist 등록");
        KidApplication offeredApplication = createOfferedApplication(parentA3, kgA, classA2, principalA, "서아", "장미반 1석 확보 후 offer 발송");
        KidApplication approvedApplication = createApprovedApplication(parentB1, kgB, classB1, principalB, kidsB.get(0), "주원", "기존 원생 승인 완료 이력");

        // 12. 업무 감사 로그 시드 생성
        createDomainAuditLog(
                kgA,
                parentA1,
                DomainAuditAction.KID_APPLICATION_WAITLISTED,
                DomainAuditTargetType.KID_APPLICATION,
                waitlistedApplication.getId(),
                "대기열 등록 샘플: " + waitlistedApplication.getKidName(),
                "{\"classroomId\":" + classA1.getId() + "}"
        );
        createDomainAuditLog(
                kgA,
                principalA,
                DomainAuditAction.KID_APPLICATION_OFFERED,
                DomainAuditTargetType.KID_APPLICATION,
                offeredApplication.getId(),
                "입학 제안 발송 샘플: " + offeredApplication.getKidName(),
                "{\"classroomId\":" + classA2.getId() + "}"
        );
        createDomainAuditLog(
                kgB,
                principalB,
                DomainAuditAction.KID_APPLICATION_APPROVED,
                DomainAuditTargetType.KID_APPLICATION,
                approvedApplication.getId(),
                "입학 승인 완료 샘플: " + approvedApplication.getKidName(),
                "{\"kidId\":" + kidsB.get(0).getId() + "}"
        );

        // 13. 알림 outbox 실패/재시도 시연용 데이터
        createDeadLetterOutbox(principalA, NotificationChannel.APP, "APP webhook timeout", "/notification-outbox");
        createDeadLetterOutbox(principalA, NotificationChannel.PUSH, "Push provider 503", "/notification-outbox");
        createDeadLetterOutbox(principalA, NotificationChannel.EMAIL, "SMTP connection refused", "/notification-outbox");
        createDeliveredOutbox(principalA, NotificationChannel.APP, "운영 알림 전달 성공", "/notifications");

        // 14. 캘린더 시연용 일정
        createCalendarEvent(kgA, null, principalA, "입학 상담 주간", "신규 학부모 상담 집중 주간입니다.",
                today.plusDays(1).atTime(10, 0), today.plusDays(1).atTime(11, 30),
                CalendarEventType.MEETING, CalendarScopeType.KINDERGARTEN, RepeatType.NONE, null);
        createCalendarEvent(kgA, classA1, teacherA1, "해바라기반 매주 미술 활동", "매주 수요일 미술 활동",
                today.plusDays(2).atTime(13, 30), today.plusDays(2).atTime(14, 20),
                CalendarEventType.LESSON, CalendarScopeType.CLASSROOM, RepeatType.WEEKLY, today.plusWeeks(4));
        createCalendarEvent(kgA, null, principalA, "운영 지표 점검", "대시보드와 outbox 상태 확인",
                today.plusDays(3).atTime(9, 30), today.plusDays(3).atTime(10, 0),
                CalendarEventType.ETC, CalendarScopeType.PERSONAL, RepeatType.NONE, null);

        log.info("=================================================");
        log.info("DUMMY DATA LOADED SUCCESSFULLY!");
        log.info("=================================================");
        if (seedProperties.isLogCredentials()) {
            log.info("Seed credentials logging enabled for local troubleshooting only.");
            log.info("TEST PASSWORD: {}", TEST_PASSWORD);
            log.info("유치원 A principal: principal@test.com");
            log.info("유치원 B principal: principal2@test.com");
            log.info("교사 예시: teacher1@test.com, teacher2@test.com, teacher3@test.com, teacher4@test.com");
            log.info("학부모 예시: parent1@test.com ~ parent6@test.com");
        } else {
            log.info("Seed credentials logging is disabled. Refer to demo/runbook documentation for sample accounts.");
        }
        log.info("=================================================");
        log.info("총 생성: 2 유치원, 2 원장, 4 선생님, 6 학부모, 4 반, 12 원아, 입학신청 4건, outbox 샘플 4건");
        log.info("=================================================");
    }

    private void supplementDemoScenarioSamplesIfPossible() {
        Optional<DemoSupplementContext> context = buildDemoSupplementContext();
        if (context.isEmpty()) {
            return;
        }

        DemoSupplementContext seed = context.get();
        KidApplication pendingApplication = kidApplicationRepository.findByParentAndKindergarten(seed.parentA1().getId(), seed.kgA().getId())
                .orElseGet(() -> createPendingApplication(seed.parentA1(), seed.kgA(), seed.classA1(), "민준", "신규 입학 상담 후 서류 대기"));
        KidApplication waitlistedApplication = kidApplicationRepository.findByParentAndKindergarten(seed.parentA2().getId(), seed.kgA().getId())
                .orElseGet(() -> createWaitlistedApplication(seed.parentA2(), seed.kgA(), seed.classA1(), seed.teacherA1(), "유나", "해바라기반 정원 대기로 waitlist 등록"));
        KidApplication offeredApplication = kidApplicationRepository.findByParentAndKindergarten(seed.parentA3().getId(), seed.kgA().getId())
                .orElseGet(() -> createOfferedApplication(seed.parentA3(), seed.kgA(), seed.classA2(), seed.principalA(), "서아", "장미반 1석 확보 후 offer 발송"));
        KidApplication approvedApplication = kidApplicationRepository.findByParentAndKindergarten(seed.parentB1().getId(), seed.kgB().getId())
                .orElseGet(() -> createApprovedApplication(seed.parentB1(), seed.kgB(), seed.classB1(), seed.principalB(), seed.approvedKid(), "주원", "기존 원생 승인 완료 이력"));

        supplementDomainAuditSamples(seed.kgA(), seed.kgB(), seed.parentA1(), seed.principalA(), seed.principalB(),
                seed.classA1(), seed.classA2(), seed.approvedKid(),
                waitlistedApplication, offeredApplication, approvedApplication);
        supplementOutboxSamples(seed.principalA());
        supplementCalendarSamples(seed.kgA(), seed.classA1(), seed.principalA(), seed.teacherA1());

        log.info("Demo scenario sample supplements checked.");
    }

    private Optional<DemoSupplementContext> buildDemoSupplementContext() {
        Optional<Member> principalA = memberRepository.findByEmail(SEED_PRINCIPAL_A_EMAIL);
        Optional<Member> principalB = memberRepository.findByEmail(SEED_PRINCIPAL_B_EMAIL);
        Optional<Member> teacherA1 = memberRepository.findByEmail("teacher1@test.com");
        Optional<Member> parentA1 = memberRepository.findByEmail("parent1@test.com");
        Optional<Member> parentA2 = memberRepository.findByEmail("parent2@test.com");
        Optional<Member> parentA3 = memberRepository.findByEmail("parent3@test.com");
        Optional<Member> parentB1 = memberRepository.findByEmail("parent4@test.com");

        if (principalA.isEmpty()
                || principalB.isEmpty()
                || teacherA1.isEmpty()
                || parentA1.isEmpty()
                || parentA2.isEmpty()
                || parentA3.isEmpty()
                || parentB1.isEmpty()) {
            log.info("Demo supplement skipped because required seed members are incomplete.");
            return Optional.empty();
        }

        Kindergarten kgA = principalA.get().getKindergarten();
        Kindergarten kgB = principalB.get().getKindergarten();
        if (kgA == null || kgB == null) {
            log.info("Demo supplement skipped because seed principals are not assigned to kindergarten.");
            return Optional.empty();
        }

        Optional<Classroom> classA1 = findClassroomByName(kgA, "해바라기반");
        Optional<Classroom> classA2 = findClassroomByName(kgA, "장미반");
        Optional<Classroom> classB1 = findClassroomByName(kgB, "나무반");
        Optional<Kid> approvedKid = findKidByName(kgB, "주원");
        if (classA1.isEmpty() || classA2.isEmpty() || classB1.isEmpty() || approvedKid.isEmpty()) {
            log.info("Demo supplement skipped because required seed classrooms or kids are incomplete.");
            return Optional.empty();
        }

        return Optional.of(new DemoSupplementContext(
                principalA.get(),
                principalB.get(),
                teacherA1.get(),
                parentA1.get(),
                parentA2.get(),
                parentA3.get(),
                parentB1.get(),
                kgA,
                kgB,
                classA1.get(),
                classA2.get(),
                classB1.get(),
                approvedKid.get()
        ));
    }

    private Optional<Classroom> findClassroomByName(Kindergarten kindergarten, String name) {
        return classroomRepository.findByKindergartenIdAndDeletedAtIsNull(kindergarten.getId())
                .stream()
                .filter(classroom -> name.equals(classroom.getName()))
                .findFirst();
    }

    private Optional<Kid> findKidByName(Kindergarten kindergarten, String name) {
        return kidRepository.findByKindergartenIdAndDeletedAtIsNull(kindergarten.getId())
                .stream()
                .filter(kid -> name.equals(kid.getName()))
                .findFirst();
    }

    private record DemoSupplementContext(
            Member principalA,
            Member principalB,
            Member teacherA1,
            Member parentA1,
            Member parentA2,
            Member parentA3,
            Member parentB1,
            Kindergarten kgA,
            Kindergarten kgB,
            Classroom classA1,
            Classroom classA2,
            Classroom classB1,
            Kid approvedKid
    ) {
    }

    private void supplementDomainAuditSamples(Kindergarten kgA,
                                              Kindergarten kgB,
                                              Member parentA1,
                                              Member principalA,
                                              Member principalB,
                                              Classroom classA1,
                                              Classroom classA2,
                                              Kid approvedKid,
                                              KidApplication waitlistedApplication,
                                              KidApplication offeredApplication,
                                              KidApplication approvedApplication) {
        if (domainAuditLogRepository.searchAllByKindergartenId(
                kgA.getId(),
                DomainAuditAction.KID_APPLICATION_WAITLISTED,
                DomainAuditTargetType.KID_APPLICATION,
                null,
                null,
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 1)
        ).isEmpty()) {
            createDomainAuditLog(
                    kgA,
                    parentA1,
                    DomainAuditAction.KID_APPLICATION_WAITLISTED,
                    DomainAuditTargetType.KID_APPLICATION,
                    waitlistedApplication.getId(),
                    "대기열 등록 샘플: " + waitlistedApplication.getKidName(),
                    "{\"classroomId\":" + classA1.getId() + "}"
            );
        }
        if (domainAuditLogRepository.searchAllByKindergartenId(
                kgA.getId(),
                DomainAuditAction.KID_APPLICATION_OFFERED,
                DomainAuditTargetType.KID_APPLICATION,
                null,
                null,
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 1)
        ).isEmpty()) {
            createDomainAuditLog(
                    kgA,
                    principalA,
                    DomainAuditAction.KID_APPLICATION_OFFERED,
                    DomainAuditTargetType.KID_APPLICATION,
                    offeredApplication.getId(),
                    "입학 제안 발송 샘플: " + offeredApplication.getKidName(),
                    "{\"classroomId\":" + classA2.getId() + "}"
            );
        }
        if (domainAuditLogRepository.searchAllByKindergartenId(
                kgB.getId(),
                DomainAuditAction.KID_APPLICATION_APPROVED,
                DomainAuditTargetType.KID_APPLICATION,
                null,
                null,
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 1)
        ).isEmpty()) {
            createDomainAuditLog(
                    kgB,
                    principalB,
                    DomainAuditAction.KID_APPLICATION_APPROVED,
                    DomainAuditTargetType.KID_APPLICATION,
                    approvedApplication.getId(),
                    "입학 승인 완료 샘플: " + approvedApplication.getKidName(),
                    "{\"kidId\":" + approvedKid.getId() + "}"
            );
        }
    }

    private void supplementOutboxSamples(Member principalA) {
        if (notificationOutboxRepository.countByStatusAndChannel(NotificationDeliveryStatus.DEAD_LETTER, NotificationChannel.APP) == 0) {
            createDeadLetterOutbox(principalA, NotificationChannel.APP, "APP webhook timeout", "/notification-outbox");
        }
        if (notificationOutboxRepository.countByStatusAndChannel(NotificationDeliveryStatus.DEAD_LETTER, NotificationChannel.PUSH) == 0) {
            createDeadLetterOutbox(principalA, NotificationChannel.PUSH, "Push provider 503", "/notification-outbox");
        }
        if (notificationOutboxRepository.countByStatusAndChannel(NotificationDeliveryStatus.DEAD_LETTER, NotificationChannel.EMAIL) == 0) {
            createDeadLetterOutbox(principalA, NotificationChannel.EMAIL, "SMTP connection refused", "/notification-outbox");
        }
        if (notificationOutboxRepository.countByStatusAndChannel(NotificationDeliveryStatus.DELIVERED, NotificationChannel.APP) == 0) {
            createDeliveredOutbox(principalA, NotificationChannel.APP, "운영 알림 전달 성공", "/notifications");
        }
    }

    private void supplementCalendarSamples(Kindergarten kgA,
                                           Classroom classA1,
                                           Member principalA,
                                           Member teacherA1) {
        LocalDate today = ProductTime.today();
        if (!calendarEventRepository.existsByKindergartenIdAndTitleAndDeletedAtIsNull(kgA.getId(), "입학 상담 주간")) {
            createCalendarEvent(kgA, null, principalA, "입학 상담 주간", "신규 학부모 상담 집중 주간입니다.",
                    today.plusDays(1).atTime(10, 0), today.plusDays(1).atTime(11, 30),
                    CalendarEventType.MEETING, CalendarScopeType.KINDERGARTEN, RepeatType.NONE, null);
        }
        if (!calendarEventRepository.existsByKindergartenIdAndTitleAndDeletedAtIsNull(kgA.getId(), "해바라기반 매주 미술 활동")) {
            createCalendarEvent(kgA, classA1, teacherA1, "해바라기반 매주 미술 활동", "매주 수요일 미술 활동",
                    today.plusDays(2).atTime(13, 30), today.plusDays(2).atTime(14, 20),
                    CalendarEventType.LESSON, CalendarScopeType.CLASSROOM, RepeatType.WEEKLY, today.plusWeeks(4));
        }
        if (!calendarEventRepository.existsByKindergartenIdAndTitleAndDeletedAtIsNull(kgA.getId(), "운영 지표 점검")) {
            createCalendarEvent(kgA, null, principalA, "운영 지표 점검", "대시보드와 outbox 상태 확인",
                    today.plusDays(3).atTime(9, 30), today.plusDays(3).atTime(10, 0),
                    CalendarEventType.ETC, CalendarScopeType.PERSONAL, RepeatType.NONE, null);
        }
    }

    private Kindergarten createKindergarten(String name, String address, String phone) {
        Kindergarten kg = Kindergarten.create(name, address, phone, LocalTime.of(9, 0), LocalTime.of(18, 0));
        return kindergartenRepository.save(kg);
    }

    private Member createMember(String email, String name, MemberRole role, Kindergarten kindergarten) {
        Member member = Member.create(email, passwordEncoder.encode(TEST_PASSWORD), name, "010-1234-5678", role);
        member.assignKindergarten(kindergarten);
        return memberRepository.save(member);
    }

    private Classroom createClassroom(Kindergarten kg, String name, String ageGroup, Member teacher) {
        Classroom classroom = Classroom.create(kg, name, ageGroup);
        classroom.assignTeacher(teacher);
        return classroomRepository.save(classroom);
    }

    private List<Kid> createKidsForClassroom(Classroom classroom, String... names) {
        List<Kid> kids = new ArrayList<>();
        int birthYear = ProductTime.today().getYear() - 5;
        for (String name : names) {
            Kid kid = Kid.create(
                    classroom,
                    name,
                    LocalDate.of(birthYear, 3, 15),
                    random.nextBoolean() ? Gender.MALE : Gender.FEMALE,
                    LocalDate.of(ProductTime.today().getYear(), 3, 1)
            );
            kids.add(kidRepository.save(kid));
        }
        return kids;
    }

    private ParentKid createParentKid(Member parent, Kid kid, Relationship relationship) {
        ParentKid parentKid = ParentKid.create(kid, parent, relationship);
        return parentKidRepository.save(parentKid);
    }

    private void createAttendance(Kid kid, LocalDate date) {
        AttendanceStatus status = determineAttendanceStatus(date);

        Attendance attendance = Attendance.create(kid, date, status);

        if (status == AttendanceStatus.PRESENT) {
            attendance.recordDropOff(LocalTime.of(9, random.nextInt(30)));
            attendance.recordPickUp(LocalTime.of(16, random.nextInt(60)));
        } else {
            attendance.updateAttendance(status, "자택 연락");
        }

        attendanceRepository.save(attendance);
    }

    private void createAuthAuditLog(Member member,
                                    AuthAuditEventType eventType,
                                    AuthAuditResult result,
                                    MemberAuthProvider provider,
                                    String reason,
                                    String clientIp) {
        authAuditLogRepository.save(AuthAuditLog.create(
                member.getId(),
                member.getKindergarten() != null ? member.getKindergarten().getId() : null,
                member.getEmail(),
                provider,
                eventType,
                result,
                reason,
                clientIp
        ));
    }

    private KidApplication createPendingApplication(Member parent,
                                                    Kindergarten kindergarten,
                                                    Classroom preferredClassroom,
                                                    String kidName,
                                                    String notes) {
        KidApplication application = KidApplication.create(
                parent,
                kindergarten,
                kidName,
                LocalDate.of(ProductTime.today().getYear() - 4, 5, 10),
                Gender.FEMALE,
                preferredClassroom,
                notes
        );
        return kidApplicationRepository.save(application);
    }

    private KidApplication createWaitlistedApplication(Member parent,
                                                       Kindergarten kindergarten,
                                                       Classroom classroom,
                                                       Member processor,
                                                       String kidName,
                                                       String note) {
        KidApplication application = createPendingApplication(parent, kindergarten, classroom, kidName, note);
        application.placeOnWaitlist(classroom, processor, note);
        return kidApplicationRepository.save(application);
    }

    private KidApplication createOfferedApplication(Member parent,
                                                    Kindergarten kindergarten,
                                                    Classroom classroom,
                                                    Member processor,
                                                    String kidName,
                                                    String note) {
        KidApplication application = createPendingApplication(parent, kindergarten, classroom, kidName, note);
        application.offerSeat(classroom, processor, ProductTime.nowDateTime().plusDays(3), note);
        return kidApplicationRepository.save(application);
    }

    private KidApplication createApprovedApplication(Member parent,
                                                     Kindergarten kindergarten,
                                                     Classroom classroom,
                                                     Member processor,
                                                     Kid approvedKid,
                                                     String kidName,
                                                     String note) {
        KidApplication application = createPendingApplication(parent, kindergarten, classroom, kidName, note);
        application.approveDirect(classroom, processor, approvedKid.getId());
        return kidApplicationRepository.save(application);
    }

    private void createDomainAuditLog(Kindergarten kindergarten,
                                      Member actor,
                                      DomainAuditAction action,
                                      DomainAuditTargetType targetType,
                                      Long targetId,
                                      String summary,
                                      String metadataJson) {
        domainAuditLogRepository.save(DomainAuditLog.create(
                kindergarten.getId(),
                actor.getId(),
                actor.getName(),
                actor.getRole(),
                action,
                targetType,
                targetId,
                summary,
                metadataJson
        ));
    }

    private void createDeadLetterOutbox(Member receiver, NotificationChannel channel, String errorMessage, String linkUrl) {
        Notification notification = notificationRepository.save(Notification.createWithLink(
                receiver,
                NotificationType.SYSTEM,
                "시연용 외부 알림 실패",
                channel + " 채널 실패 시나리오입니다.",
                linkUrl
        ));
        NotificationOutbox outbox = NotificationOutbox.create(notification, channel, 2);
        LocalDateTime firstAttemptAt = ProductTime.nowDateTime().minusMinutes(10);
        outbox.markProcessing(firstAttemptAt);
        outbox.markDeadLetter(ProductTime.nowDateTime().minusMinutes(8), errorMessage);
        notificationOutboxRepository.save(outbox);
    }

    private void createDeliveredOutbox(Member receiver, NotificationChannel channel, String title, String linkUrl) {
        Notification notification = notificationRepository.save(Notification.createWithLink(
                receiver,
                NotificationType.SYSTEM,
                title,
                "정상 전달된 outbox 샘플입니다.",
                linkUrl
        ));
        NotificationOutbox outbox = NotificationOutbox.create(notification, channel, 2);
        LocalDateTime attemptAt = ProductTime.nowDateTime().minusMinutes(3);
        outbox.markProcessing(attemptAt);
        outbox.markDelivered(attemptAt.plusSeconds(2));
        notificationOutboxRepository.save(outbox);
    }

    private void createCalendarEvent(Kindergarten kindergarten,
                                     Classroom classroom,
                                     Member creator,
                                     String title,
                                     String description,
                                     LocalDateTime startDateTime,
                                     LocalDateTime endDateTime,
                                     CalendarEventType eventType,
                                     CalendarScopeType scopeType,
                                     RepeatType repeatType,
                                     LocalDate repeatEndDate) {
        calendarEventRepository.save(CalendarEvent.create(
                kindergarten,
                classroom,
                creator,
                title,
                description,
                startDateTime,
                endDateTime,
                eventType,
                scopeType,
                false,
                null,
                repeatType,
                repeatEndDate
        ));
    }

    private AttendanceStatus determineAttendanceStatus(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        int dayValue = dayOfWeek.getValue();
        int statusIndex = dayValue % 5;

        return switch (statusIndex) {
            case 0 -> AttendanceStatus.ABSENT;
            case 1 -> AttendanceStatus.LATE;
            case 2 -> AttendanceStatus.EARLY_LEAVE;
            case 3 -> AttendanceStatus.SICK_LEAVE;
            default -> AttendanceStatus.PRESENT;
        };
    }

    private void createNotepad(Classroom classroom, Member writer, String title, String content, Kid kid) {
        Notepad notepad;
        if (kid != null) {
            notepad = Notepad.createKidNotepad(kid, writer, title, content);
        } else {
            notepad = Notepad.createClassroomNotepad(classroom, writer, title, content);
        }
        notepadRepository.save(notepad);
    }

    private void createAnnouncement(Kindergarten kg, Member writer, String title, String content, boolean important) {
        Announcement announcement;
        if (important) {
            announcement = Announcement.createImportant(kg, writer, title, content);
        } else {
            announcement = Announcement.create(kg, writer, title, content);
        }
        announcementRepository.save(announcement);
    }
}
