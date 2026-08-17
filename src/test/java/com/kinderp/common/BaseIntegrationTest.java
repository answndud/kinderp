package com.kinderp.common;

import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.repository.MemberRepository;
import com.kinderp.global.security.user.CustomUserDetails;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.entity.Relationship;
import com.kinderp.domain.kid.entity.ParentKid;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.kid.repository.ParentKidRepository;
import com.kinderp.domain.attendance.entity.Attendance;
import com.kinderp.domain.attendance.entity.AttendanceStatus;
import com.kinderp.domain.attendance.repository.AttendanceRepository;
import com.kinderp.domain.notepad.entity.Notepad;
import com.kinderp.domain.notepad.repository.NotepadRepository;
import com.kinderp.domain.announcement.entity.Announcement;
import com.kinderp.domain.announcement.repository.AnnouncementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * 통합 테스트 기반 클래스
 * 모든 통합 테스트가 상속받아 사용하는 공통 설정 포함
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
@Tag("integration")
public abstract class BaseIntegrationTest extends TestcontainersSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected KindergartenRepository kindergartenRepository;

    @Autowired
    protected ClassroomRepository classroomRepository;

    @Autowired
    protected KidRepository kidRepository;

    @Autowired
    protected ParentKidRepository parentKidRepository;

    @Autowired
    protected AttendanceRepository attendanceRepository;

    @Autowired
    protected NotepadRepository notepadRepository;

    @Autowired
    protected AnnouncementRepository announcementRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestData testData;

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected Member principalMember;
    protected Member teacherMember;
    protected Member parentMember;
    protected Kindergarten kindergarten;
    protected Classroom classroom;
    protected Kid kid;
    protected Attendance attendance;
    protected Notepad notepad;
    protected Announcement announcement;

    @BeforeEach
    void setUp() {
        clearRedis();
        writeCommitted(() -> {
            testData.cleanup();
            resetIdentity();
            return null;
        });

        // 테스트 데이터 초기화
        principalMember = testData.createPrincipalMember();
        teacherMember = testData.createTeacherMember();
        parentMember = testData.createParentMember();

        kindergarten = testData.createKindergarten();
        principalMember.assignKindergarten(kindergarten);
        teacherMember.assignKindergarten(kindergarten);
        parentMember.assignKindergarten(kindergarten);
        memberRepository.save(principalMember);
        memberRepository.save(teacherMember);
        memberRepository.save(parentMember);

        classroom = testData.createClassroom(kindergarten);
        classroom.assignTeacher(teacherMember);
        classroomRepository.save(classroom);

        kid = Kid.create(classroom, "테스트 원생", java.time.LocalDate.of(2020, 1, 1),
                Gender.MALE, java.time.LocalDate.now());
        kidRepository.save(kid);

        kid.addParent(parentMember, Relationship.MOTHER);
        kidRepository.save(kid);

        attendance = Attendance.create(kid, java.time.LocalDate.of(2025, 1, 13), AttendanceStatus.PRESENT);
        attendance.recordDropOff(java.time.LocalTime.of(9, 0));
        attendance.recordPickUp(java.time.LocalTime.of(16, 0));
        attendanceRepository.save(attendance);

        notepad = Notepad.createClassroomNotepad(classroom, teacherMember, "테스트 알림장", "테스트 내용");
        notepadRepository.save(notepad);

        announcement = Announcement.create(kindergarten, principalMember, "테스트 공지", "테스트 공지 내용");
        announcementRepository.save(announcement);

        replaceAuthenticationPrincipal();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 인증된 사용자로 SecurityContext 설정
     */
    protected void setAuthentication(Member member) {
        CustomUserDetails customUserDetails = new CustomUserDetails(member);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities())
        );
    }

    /**
     * 원장 인증 상태 설정
     */
    protected void authenticateAsPrincipal() {
        setAuthentication(principalMember);
    }

    /**
     * 교사 인증 상태 설정
     */
    protected void authenticateAsTeacher() {
        setAuthentication(teacherMember);
    }

    /**
     * 학부모 인증 상태 설정
     */
    protected void authenticateAsParent() {
        setAuthentication(parentMember);
    }

    /**
     * 인증 해제
     */
    protected void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    protected void clearRedis() {
        var connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }

        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    protected Member createMemberInKindergarten(String email, String name, com.kinderp.domain.member.entity.MemberRole role,
                                                Kindergarten targetKindergarten) {
        Member member = testData.createTestMember(email, name, role, "test1234");
        member.assignKindergarten(targetKindergarten);
        return memberRepository.save(member);
    }

    protected RequestPostProcessor authenticated(Member member) {
        return user(new CustomUserDetails(member));
    }

    protected <T> T readCommitted(Supplier<T> supplier) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> supplier.get());
    }

    protected <T> T writeCommitted(Supplier<T> supplier) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void replaceAuthenticationPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return;
        }
        if (!(principal instanceof UserDetails userDetails)) {
            return;
        }

        memberRepository.findByEmail(userDetails.getUsername())
                .ifPresent(member -> {
                    CustomUserDetails customUserDetails = new CustomUserDetails(member);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    customUserDetails,
                                    null,
                                    customUserDetails.getAuthorities()
                            )
                    );
                });
    }

    private void resetIdentity() {
        resetTableIdentity("domain_audit_log");
        resetTableIdentity("attendance_change_request");
        resetTableIdentity("notification_outbox");
        resetTableIdentity("auth_audit_log");
        resetTableIdentity("member_social_account");
        resetTableIdentity("announcement_view");
        resetTableIdentity("attendance");
        resetTableIdentity("notepad");
        resetTableIdentity("announcement");
        resetTableIdentity("parent_kid");
        resetTableIdentity("kid");
        resetTableIdentity("classroom");
        resetTableIdentity("kindergarten");
        resetTableIdentity("member");
    }

    private void resetTableIdentity(String tableName) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1");
    }
}
