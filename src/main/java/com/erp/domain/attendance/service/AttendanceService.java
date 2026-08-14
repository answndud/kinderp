package com.erp.domain.attendance.service;

import com.erp.domain.attendance.dto.request.AttendanceRequest;
import com.erp.domain.attendance.dto.request.BulkAttendanceRequest;
import com.erp.domain.attendance.dto.request.DropOffRequest;
import com.erp.domain.attendance.dto.request.PickUpRequest;
import com.erp.domain.attendance.dto.response.AttendanceResponse;
import com.erp.domain.attendance.dto.response.AttendanceDashboardSummaryResponse;
import com.erp.domain.attendance.dto.response.DailyAttendanceResponse;
import com.erp.domain.attendance.dto.response.MonthlyAttendanceKidReportResponse;
import com.erp.domain.attendance.dto.response.MonthlyAttendanceReportResponse;
import com.erp.domain.attendance.dto.response.MonthlyStatisticsResponse;
import com.erp.domain.attendance.entity.Attendance;
import com.erp.domain.attendance.entity.AttendanceStatus;
import com.erp.domain.attendance.repository.AttendanceRepository;
import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.classroom.service.ClassroomService;
import com.erp.domain.dashboard.service.DashboardService;
import com.erp.domain.kid.entity.Kid;
import com.erp.domain.kid.service.KidService;
import com.erp.domain.member.entity.Member;
import com.erp.domain.member.entity.MemberRole;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import com.erp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 출석 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final KidService kidService;
    private final ClassroomService classroomService;
    private final DashboardService dashboardService;
    private final AccessPolicyService accessPolicyService;

    @Transactional
    public Long createAttendance(AttendanceRequest request, Long requesterId) {
        Kid kid = getKidForManage(requesterId, request.getKidId());
        Attendance attendance = findOrCreateAttendance(kid, request.getDate(), request.getStatus());

        applyStatus(attendance,
                request.getStatus(),
                request.getNote(),
                request.getDropOffTime(),
                request.getPickUpTime());

        Attendance saved = attendanceRepository.save(attendance);
        evictDashboardStatisticsByAttendance(saved);
        return saved.getId();
    }

    public Attendance getAttendance(Long id, Long requesterId) {
        return getAttendanceForRead(requesterId, id);
    }

    public Attendance getAttendanceByKidAndDate(Long kidId, LocalDate date, Long requesterId) {
        getKidForRead(requesterId, kidId);
        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));
        accessPolicyService.validateAttendanceReadAccess(requester(requesterId), attendance);
        return attendance;
    }

    public List<Attendance> getAttendancesByClassroomAndDate(Long classroomId, LocalDate date, Long requesterId) {
        getClassroomForRead(requesterId, classroomId);
        return attendanceRepository.findByClassroomIdAndDate(classroomId, date);
    }

    public List<Attendance> getAttendancesByKidAndMonth(Long kidId, int year, int month, Long requesterId) {
        getKidForRead(requesterId, kidId);
        YearMonthRange range = resolveYearMonthRange(year, month);
        return attendanceRepository.findByKidIdAndDateBetween(kidId, range.startDate(), range.endDate());
    }

    @Transactional
    public void updateAttendance(Long id, AttendanceRequest request, Long requesterId) {
        Attendance attendance = getAttendanceForManage(requesterId, id);
        applyStatus(attendance,
                request.getStatus(),
                request.getNote(),
                request.getDropOffTime(),
                request.getPickUpTime());
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public AttendanceResponse upsertAttendance(AttendanceRequest request, Long requesterId) {
        Kid kid = getKidForManage(requesterId, request.getKidId());
        Attendance attendance = findOrCreateAttendance(kid, request.getDate(), request.getStatus());

        applyStatus(attendance,
                request.getStatus(),
                request.getNote(),
                request.getDropOffTime(),
                request.getPickUpTime());

        Attendance saved = attendanceRepository.save(attendance);
        evictDashboardStatisticsByAttendance(saved);
        return AttendanceResponse.from(saved);
    }

    @Transactional
    public int bulkUpdateAttendance(BulkAttendanceRequest request, Long requesterId) {
        Classroom classroom = getClassroomForManage(requesterId, request.getClassroomId());
        List<Long> kidIds;
        if (request.getKidIds() == null || request.getKidIds().isEmpty()) {
            kidIds = kidService.getKidsByClassroom(request.getClassroomId(), requesterId).stream()
                    .map(Kid::getId)
                    .toList();
        } else {
            kidIds = request.getKidIds();
        }

        int updated = 0;
        for (Long kidId : kidIds) {
            Kid kid = getKidForManage(requesterId, kidId);
            validateKidBelongsToClassroom(kid, classroom);

            Attendance attendance = findOrCreateAttendance(kid, request.getDate(), request.getStatus());

            applyStatus(attendance,
                    request.getStatus(),
                    request.getNote(),
                    request.getDropOffTime(),
                    request.getPickUpTime());

            attendanceRepository.save(attendance);
            updated++;
        }

        if (updated > 0) {
            dashboardService.evictDashboardStatisticsCache(classroom.getKindergarten().getId());
        }

        return updated;
    }

    @Transactional
    public void recordDropOff(Long kidId, LocalDate date, DropOffRequest request, Long requesterId) {
        Kid kid = getKidForManage(requesterId, kidId);
        LocalTime dropOffTime = resolveTimeOrNow(request.getDropOffTime());

        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseGet(() -> attendanceRepository.save(Attendance.createDropOff(kid, date, dropOffTime)));

        attendance.recordDropOff(dropOffTime);
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public void recordPickUp(Long kidId, LocalDate date, PickUpRequest request, Long requesterId) {
        getKidForManage(requesterId, kidId);
        LocalTime pickUpTime = resolveTimeOrNow(request.getPickUpTime());

        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));

        accessPolicyService.validateAttendanceManageAccess(requester(requesterId), attendance.getKid());
        attendance.recordPickUp(pickUpTime);
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public void markAbsent(Long kidId, LocalDate date, String note, Long requesterId) {
        Kid kid = getKidForManage(requesterId, kidId);
        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseGet(() -> attendanceRepository.save(Attendance.create(kid, date, AttendanceStatus.ABSENT)));

        attendance.markAbsent(note);
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public void markLate(Long kidId, LocalDate date, java.time.LocalTime dropOffTime, String note, Long requesterId) {
        Kid kid = getKidForManage(requesterId, kidId);
        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseGet(() -> attendanceRepository.save(Attendance.create(kid, date, AttendanceStatus.LATE)));

        attendance.markLate(dropOffTime, note);
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public void markEarlyLeave(Long kidId, LocalDate date, java.time.LocalTime pickUpTime, String note, Long requesterId) {
        getKidForManage(requesterId, kidId);
        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));

        accessPolicyService.validateAttendanceManageAccess(requester(requesterId), attendance.getKid());
        attendance.markEarlyLeave(pickUpTime, note);
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public void markSickLeave(Long kidId, LocalDate date, String note, Long requesterId) {
        Kid kid = getKidForManage(requesterId, kidId);
        Attendance attendance = attendanceRepository.findByKidIdAndDate(kidId, date)
                .orElseGet(() -> attendanceRepository.save(Attendance.create(kid, date, AttendanceStatus.SICK_LEAVE)));

        attendance.markSickLeave(note);
        evictDashboardStatisticsByAttendance(attendance);
    }

    @Transactional
    public void deleteAttendance(Long id, Long requesterId) {
        Attendance attendance = getAttendanceForManage(requesterId, id);
        evictDashboardStatisticsByAttendance(attendance);
        attendanceRepository.delete(attendance);
    }

    public MonthlyStatisticsResponse getMonthlyStatistics(Long kidId, int year, int month, Long requesterId) {
        Kid kid = getKidForRead(requesterId, kidId);
        YearMonthRange range = resolveYearMonthRange(year, month);

        long presentDays = attendanceRepository.countPresentDaysByKidIdAndDateBetween(kidId, range.startDate(), range.endDate());
        long absentDays = attendanceRepository.countAbsentDaysByKidIdAndDateBetween(kidId, range.startDate(), range.endDate());
        long lateDays = attendanceRepository.countByKidIdAndDateBetweenAndStatus(
                kidId, range.startDate(), range.endDate(), AttendanceStatus.LATE);
        long sickLeaveDays = attendanceRepository.countByKidIdAndDateBetweenAndStatus(
                kidId, range.startDate(), range.endDate(), AttendanceStatus.SICK_LEAVE);
        List<Attendance> allAttendances = attendanceRepository.findByKidIdAndDateBetween(
                kidId, range.startDate(), range.endDate());

        return new MonthlyStatisticsResponse(
                kidId,
                kid.getName(),
                year,
                month,
                (int) presentDays,
                (int) absentDays,
                (int) lateDays,
                (int) sickLeaveDays,
                allAttendances.size()
        );
    }

    public List<DailyAttendanceResponse> getDailyAttendanceByClassroom(Long classroomId, LocalDate date, Long requesterId) {
        List<Kid> kids = kidService.getKidsByClassroom(classroomId, requesterId);
        List<Attendance> attendances = getAttendancesByClassroomAndDate(classroomId, date, requesterId);
        java.util.Map<Long, Attendance> attendanceMap = attendances.stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getKid().getId(), a -> a));

        return kids.stream()
                .map(kid -> DailyAttendanceResponse.from(kid, attendanceMap.get(kid.getId())))
                .collect(Collectors.toList());
    }

    public AttendanceDashboardSummaryResponse getDashboardSummary(
            LocalDate startDate,
            LocalDate endDate,
            Long requesterId
    ) {
        if (startDate == null || endDate == null
                || startDate.isAfter(endDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "출결 조회 기간은 1일 이상 31일 이하여야 합니다");
        }

        Member requester = requester(requesterId);
        List<Kid> accessibleKids = resolveDashboardKids(requester);
        if (accessibleKids.isEmpty()) {
            return new AttendanceDashboardSummaryResponse(0, 0, 0.0);
        }

        List<Long> kidIds = accessibleKids.stream().map(Kid::getId).toList();
        Map<LocalDate, Long> presentCountByDate = attendanceRepository
                .findPresentCountsByKidIdsAndDateBetween(kidIds, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        AttendanceRepository.DailyPresentCountProjection::getDate,
                        AttendanceRepository.DailyPresentCountProjection::getPresentCount
                ));

        long expectedCount = countActiveKidSchoolDays(accessibleKids, startDate, endDate);
        long presentCount = presentCountByDate.entrySet().stream()
                .filter(entry -> isSchoolDay(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        double attendanceRate = expectedCount == 0 ? 0.0 : presentCount * 100.0 / expectedCount;

        return new AttendanceDashboardSummaryResponse(presentCount, expectedCount, attendanceRate);
    }

    public MonthlyAttendanceReportResponse getMonthlyReportByClassroom(Long classroomId, int year, int month, Long requesterId) {
        Classroom classroom = getClassroomForRead(requesterId, classroomId);
        YearMonthRange range = resolveYearMonthRange(year, month);

        List<Kid> kids = kidService.getKidsByClassroom(classroomId, requesterId);
        List<Attendance> attendances = attendanceRepository.findByClassroomIdAndDateBetween(
                classroomId, range.startDate(), range.endDate());
        java.util.Map<Long, List<Attendance>> grouped = attendances.stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getKid().getId()));

        List<MonthlyAttendanceKidReportResponse> kidReports = kids.stream()
                .map(kid -> buildKidReport(kid, grouped.getOrDefault(kid.getId(), List.of())))
                .toList();

        return new MonthlyAttendanceReportResponse(
                classroom.getId(),
                classroom.getName(),
                year,
                month,
                kidReports
        );
    }

    /**
     * 출석 Response 변환
     */
    public AttendanceResponse toResponse(Attendance attendance) {
        return AttendanceResponse.from(attendance);
    }

    private void applyStatus(Attendance attendance,
                             AttendanceStatus status,
                             String note,
                             LocalTime dropOffTime,
                             LocalTime pickUpTime) {
        if (status == AttendanceStatus.ABSENT) {
            attendance.markAbsent(note);
            return;
        }
        if (status == AttendanceStatus.SICK_LEAVE) {
            attendance.markSickLeave(note);
            return;
        }
        if (status == AttendanceStatus.LATE) {
            attendance.markLate(dropOffTime, note);
            return;
        }
        if (status == AttendanceStatus.EARLY_LEAVE) {
            attendance.markEarlyLeave(pickUpTime, note);
            return;
        }

        attendance.updateAttendance(status, note);
        if (dropOffTime != null) {
            attendance.recordDropOff(dropOffTime);
        }
        if (pickUpTime != null) {
            attendance.recordPickUp(pickUpTime);
        }
    }

    private MonthlyAttendanceKidReportResponse buildKidReport(com.erp.domain.kid.entity.Kid kid,
                                                              List<Attendance> attendances) {
        int presentDays = 0;
        int absentDays = 0;
        int lateDays = 0;
        int earlyLeaveDays = 0;
        int sickLeaveDays = 0;

        for (Attendance attendance : attendances) {
            AttendanceStatus status = attendance.getStatus();
            if (status == AttendanceStatus.PRESENT) {
                presentDays++;
            } else if (status == AttendanceStatus.ABSENT) {
                absentDays++;
            } else if (status == AttendanceStatus.LATE) {
                lateDays++;
                presentDays++;
            } else if (status == AttendanceStatus.EARLY_LEAVE) {
                earlyLeaveDays++;
                presentDays++;
            } else if (status == AttendanceStatus.SICK_LEAVE) {
                sickLeaveDays++;
                absentDays++;
            }
        }

        return new MonthlyAttendanceKidReportResponse(
                kid.getId(),
                kid.getName(),
                presentDays,
                absentDays,
                lateDays,
                earlyLeaveDays,
                sickLeaveDays,
                attendances.size()
        );
    }

    private void evictDashboardStatisticsByAttendance(Attendance attendance) {
        Long kindergartenId = attendance.getKid().getClassroom().getKindergarten().getId();
        dashboardService.evictDashboardStatisticsCache(kindergartenId);
    }

    private Attendance findOrCreateAttendance(Kid kid, LocalDate date, AttendanceStatus initialStatus) {
        return attendanceRepository.findByKidIdAndDate(kid.getId(), date)
                .orElseGet(() -> Attendance.create(kid, date, initialStatus));
    }

    private LocalTime resolveTimeOrNow(LocalTime time) {
        return time != null ? time : LocalTime.now();
    }

    private YearMonthRange resolveYearMonthRange(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "조회 연월이 올바르지 않습니다");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        return new YearMonthRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    private List<Kid> resolveDashboardKids(Member requester) {
        if (requester.getRole() == MemberRole.PRINCIPAL) {
            return kidService.getKidsByKindergarten(requester.getKindergarten().getId(), requester.getId());
        }
        if (requester.getRole() == MemberRole.TEACHER) {
            return classroomService.getClassroomByTeacher(requester.getId())
                    .map(classroom -> kidService.getKidsByClassroom(classroom.getId(), requester.getId()))
                    .orElseGet(List::of);
        }
        return kidService.getKidsByParent(requester.getId());
    }

    private long countActiveKidSchoolDays(List<Kid> kids, LocalDate startDate, LocalDate endDate) {
        long total = 0L;
        for (Kid kid : kids) {
            LocalDate activeStart = kid.getAdmissionDate() == null
                    ? startDate
                    : maxDate(startDate, kid.getAdmissionDate());
            LocalDate deletedDate = kid.getDeletedAt() == null ? null : kid.getDeletedAt().toLocalDate();
            LocalDate activeEnd = deletedDate == null ? endDate : minDate(endDate, deletedDate);
            for (LocalDate date = activeStart; !date.isAfter(activeEnd); date = date.plusDays(1)) {
                if (isSchoolDay(date)) total++;
            }
        }
        return total;
    }

    private boolean isSchoolDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private LocalDate maxDate(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDate minDate(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private void validateKidBelongsToClassroom(Kid kid, Classroom classroom) {
        if (!classroom.getId().equals(kid.getClassroom().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "반에 속하지 않은 원생이 포함되어 있습니다");
        }
    }

    private Member requester(Long requesterId) {
        return accessPolicyService.getRequester(requesterId);
    }

    private Kid getKidForRead(Long requesterId, Long kidId) {
        Kid kid = kidService.getKid(kidId);
        accessPolicyService.validateKidReadAccess(requester(requesterId), kid);
        return kid;
    }

    private Kid getKidForManage(Long requesterId, Long kidId) {
        Kid kid = kidService.getKid(kidId);
        accessPolicyService.validateAttendanceManageAccess(requester(requesterId), kid);
        return kid;
    }

    private Classroom getClassroomForRead(Long requesterId, Long classroomId) {
        Classroom classroom = classroomService.getClassroom(classroomId);
        accessPolicyService.validateClassroomReadAccess(requester(requesterId), classroom);
        return classroom;
    }

    private Classroom getClassroomForManage(Long requesterId, Long classroomId) {
        Classroom classroom = classroomService.getClassroom(classroomId);
        accessPolicyService.validateClassroomManageAccess(requester(requesterId), classroom);
        return classroom;
    }

    private Attendance getAttendanceForRead(Long requesterId, Long attendanceId) {
        Attendance attendance = findAttendance(attendanceId);
        accessPolicyService.validateAttendanceReadAccess(requester(requesterId), attendance);
        return attendance;
    }

    private Attendance getAttendanceForManage(Long requesterId, Long attendanceId) {
        Attendance attendance = findAttendance(attendanceId);
        accessPolicyService.validateAttendanceManageAccess(requester(requesterId), attendance.getKid());
        return attendance;
    }

    private Attendance findAttendance(Long attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));
    }

    private record YearMonthRange(LocalDate startDate, LocalDate endDate) {
    }
}
