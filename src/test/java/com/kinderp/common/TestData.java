package com.kinderp.common;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.classroom.repository.ClassroomRepository;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.kid.repository.ParentKidRepository;
import com.kinderp.domain.attendance.repository.AttendanceRepository;
import com.kinderp.domain.notepad.repository.NotepadRepository;
import com.kinderp.domain.announcement.repository.AnnouncementRepository;
import jakarta.persistence.EntityManager;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.entity.MemberStatus;
import com.kinderp.domain.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 테스트용 데이터 헬퍼 클래스
 */
public class TestData {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final KindergartenRepository kindergartenRepository;
    private final ClassroomRepository classroomRepository;
    private final KidRepository kidRepository;
    private final ParentKidRepository parentKidRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotepadRepository notepadRepository;
    private final AnnouncementRepository announcementRepository;
    private final EntityManager entityManager;

    public TestData(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                  KindergartenRepository kindergartenRepository, ClassroomRepository classroomRepository,
                  KidRepository kidRepository, ParentKidRepository parentKidRepository,
                  AttendanceRepository attendanceRepository, NotepadRepository notepadRepository,
                  AnnouncementRepository announcementRepository, EntityManager entityManager) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.kindergartenRepository = kindergartenRepository;
        this.classroomRepository = classroomRepository;
        this.kidRepository = kidRepository;
        this.parentKidRepository = parentKidRepository;
        this.attendanceRepository = attendanceRepository;
        this.notepadRepository = notepadRepository;
        this.announcementRepository = announcementRepository;
        this.entityManager = entityManager;
    }

    /**
     * 테스트용 회원 생성
     */
    public Member createTestMember(String email, String name, MemberRole role, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        Member member = Member.create(email, encodedPassword, name, "010-0000-0000", role);
        member.activate(); // 활성 상태로 변경
        return memberRepository.save(member);
    }

    /**
     * 테스트용 원장 회원 생성
     */
    public Member createPrincipalMember() {
        return createTestMember("principal@test.com", "원장님", MemberRole.PRINCIPAL, "test1234");
    }

    /**
     * 테스트용 교사 회원 생성
     */
    public Member createTeacherMember() {
        return createTestMember("teacher@test.com", "김선생", MemberRole.TEACHER, "test1234");
    }

    /**
     * 테스트용 학부모 회원 생성
     */
    public Member createParentMember() {
        return createTestMember("parent@test.com", "학부모", MemberRole.PARENT, "test1234");
    }

    /**
     * 테스트용 유치원 생성
     */
    public Kindergarten createKindergarten() {
        Kindergarten kindergarten = Kindergarten.create("테스트 유치원", "서울시", "010-0000-0000",
                LocalTime.of(9, 0), LocalTime.of(18, 0));
        return kindergartenRepository.save(kindergarten);
    }

    /**
     * 테스트용 반 생성
     */
    public Classroom createClassroom(Kindergarten kindergarten) {
        Classroom classroom = Classroom.create(kindergarten, "테스트반", "5세");
        return classroomRepository.save(classroom);
    }

    /**
     * 테스트용 원생 생성
     */
    public Kid createKid(Classroom classroom) {
        Kid kid = Kid.create(classroom, "테스트 원생", LocalDate.of(2020, 1, 1),
                Gender.MALE, LocalDate.now());
        return kidRepository.save(kid);
    }

    /**
     * 모든 테스트 데이터 정리
     */
    public void cleanup() {
        entityManager.flush();
        entityManager.clear();

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notepad_read_confirm").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notification_outbox").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notification").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM auth_audit_log_archive").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM auth_audit_log").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM domain_audit_log").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM announcement_view").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM attendance_change_request").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM attendance").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notepad").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM announcement").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM calendar_event").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM kid_application").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM kindergarten_application").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM parent_kid").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM kid").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM classroom").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM member_social_account").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM member").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM kindergarten").executeUpdate();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
