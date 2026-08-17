package com.kinderp.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import com.kinderp.domain.attendance.entity.AttendanceChangeRequest;
import com.kinderp.domain.attendance.entity.AttendanceChangeRequestStatus;
import com.kinderp.domain.attendance.entity.AttendanceStatus;
import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kid.entity.Gender;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kidapplication.entity.ApplicationStatus;
import com.kinderp.domain.kidapplication.entity.KidApplication;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("운영 workflow 상태 전이 테스트")
class WorkflowStateTransitionTest {

    @Test
    @DisplayName("출결 변경 요청은 PENDING에서만 승인·거절·취소할 수 있다")
    void attendanceChangeRequest_AllReviewActionsRequirePending() {
        AttendanceChangeRequest request = createAttendanceChangeRequest();
        Member reviewer = Member.create("reviewer@test.com", "encoded", "교사", "010", MemberRole.TEACHER);

        request.approve(reviewer, 42L);

        assertThat(request.getStatus()).isEqualTo(AttendanceChangeRequestStatus.APPROVED);
        assertThat(request.getAttendanceId()).isEqualTo(42L);
        assertAlreadyProcessed(() -> request.reject(reviewer, "중복 처리"));
        assertAlreadyProcessed(request::cancel);
    }

    @Test
    @DisplayName("입학 신청은 WAITLISTED를 거쳐 OFFERED를 수락할 수 있고 종결 상태에서는 재처리할 수 없다")
    void kidApplication_EnforcesReviewStateMachine() {
        Kindergarten kindergarten = kindergarten();
        Classroom classroom = Classroom.create(kindergarten, "해바라기반", "5세");
        Member parent = Member.create("parent@test.com", "encoded", "학부모", "010", MemberRole.PARENT);
        Member processor = Member.create("principal@test.com", "encoded", "원장", "010", MemberRole.PRINCIPAL);
        KidApplication application = KidApplication.create(
                parent,
                kindergarten,
                "아이",
                LocalDate.of(2020, 1, 1),
                Gender.MALE,
                classroom,
                null
        );

        application.placeOnWaitlist(classroom, processor, "정원 대기");
        application.offerSeat(classroom, processor, java.time.LocalDateTime.now().plusMinutes(10), "자리 발생");
        application.acceptOffer(99L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getKidId()).isEqualTo(99L);
        assertThatThrownBy(() -> application.reject("중복 처리", processor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_INVALID_STATE);
    }

    private AttendanceChangeRequest createAttendanceChangeRequest() {
        Kindergarten kindergarten = kindergarten();
        Classroom classroom = Classroom.create(kindergarten, "해바라기반", "5세");
        Member parent = Member.create("requester@test.com", "encoded", "학부모", "010", MemberRole.PARENT);
        Kid kid = Kid.create(
                classroom,
                "아이",
                LocalDate.of(2020, 1, 1),
                Gender.MALE,
                LocalDate.now()
        );
        return AttendanceChangeRequest.create(
                kid,
                parent,
                LocalDate.of(2026, 8, 14),
                AttendanceStatus.PRESENT,
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                "정정 요청"
        );
    }

    private Kindergarten kindergarten() {
        return Kindergarten.create("테스트 유치원", "서울", "010", LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    private void assertAlreadyProcessed(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_CHANGE_REQUEST_NOT_PENDING);
    }
}
