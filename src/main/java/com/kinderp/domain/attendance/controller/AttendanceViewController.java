package com.kinderp.domain.attendance.controller;

import com.kinderp.domain.attendance.dto.request.AttendanceRequest;
import com.kinderp.domain.attendance.entity.Attendance;
import com.kinderp.domain.attendance.service.AttendanceService;
import com.kinderp.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * 출결 뷰 컨트롤러
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AttendanceViewController {

    private final AttendanceService attendanceService;

    /**
     * 출결관리 목록 페이지
     */
    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String attendancePage(@AuthenticationPrincipal Object userDetails) {
        return "attendance/attendance";
    }

    /**
     * 출석 등록 페이지
     */
    @GetMapping("/attendance/write")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String writeForm(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return "attendance/write";
    }

    /**
     * 출석 상세 페이지
     */
    @GetMapping("/attendance/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String detail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Attendance attendance = attendanceService.getAttendance(id, userDetails.getMemberId());
        model.addAttribute("attendance", attendanceService.toResponse(attendance));
        return "attendance/detail";
    }

    /**
     * 출석 수정 페이지
     */
    @GetMapping("/attendance/{id}/edit")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Attendance attendance = attendanceService.getAttendance(id, userDetails.getMemberId());
        model.addAttribute("attendance", attendanceService.toResponse(attendance));
        return "attendance/edit";
    }

    /**
     * 반별 일별 출석 페이지
     */
    @GetMapping("/attendance/daily")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER', 'PARENT')")
    public String dailyAttendance(
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        if (classroomId != null && date != null) {
            var dailyAttendance = attendanceService.getDailyAttendanceByClassroom(classroomId, date, userDetails.getMemberId());
            model.addAttribute("dailyAttendance", dailyAttendance);
            model.addAttribute("classroomId", classroomId);
            model.addAttribute("date", date);
        }
        return "attendance/daily";
    }

    /**
     * 월간 리포트 페이지
     */
    @GetMapping("/attendance/monthly")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String monthlyReport() {
        return "attendance/monthly";
    }

    /**
     * 출석 등록
     */
    @PostMapping("/attendance")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String write(
            @ModelAttribute AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            Long attendanceId = attendanceService.createAttendance(request, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "출석이 등록되었습니다.");
            return "redirect:/attendance/" + attendanceId;
        } catch (Exception e) {
            log.error("출석 등록 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "출석 등록에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/attendance/write";
        }
    }

    /**
     * 출석 수정
     */
    @PostMapping("/attendance/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String update(
            @PathVariable Long id,
            @ModelAttribute AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            attendanceService.updateAttendance(id, request, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "출석 정보가 수정되었습니다.");
            return "redirect:/attendance/" + id;
        } catch (Exception e) {
            log.error("출석 수정 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "출석 수정에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/attendance/" + id + "/edit";
        }
    }

    /**
     * 출석 삭제
     */
    @PostMapping("/attendance/{id}/delete")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            attendanceService.deleteAttendance(id, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "출석 정보가 삭제되었습니다.");
            return "redirect:/attendance";
        } catch (Exception e) {
            log.error("출석 삭제 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "출석 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/attendance/" + id;
        }
    }

    /**
     * 등원 기록
     */
    @PostMapping("/attendance/kid/{kidId}/drop-off")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String recordDropOff(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) java.time.LocalTime dropOffTime,
            @RequestParam(required = false) String guardianName,
            @RequestParam(required = false) Long classroomId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            var request = new com.kinderp.domain.attendance.dto.request.DropOffRequest();
            request.setDropOffTime(dropOffTime);
            attendanceService.recordDropOff(kidId, date, request, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "등원이 기록되었습니다.");
        } catch (Exception e) {
            log.error("등원 기록 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "등원 기록에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/attendance/daily?classroomId=" + classroomId + "&date=" + date;
    }

    /**
     * 하원 기록
     */
    @PostMapping("/attendance/kid/{kidId}/pick-up")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String recordPickUp(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) java.time.LocalTime pickUpTime,
            @RequestParam(required = false) String guardianName,
            @RequestParam(required = false) Long classroomId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            var request = new com.kinderp.domain.attendance.dto.request.PickUpRequest();
            request.setPickUpTime(pickUpTime);
            attendanceService.recordPickUp(kidId, date, request, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "하원이 기록되었습니다.");
        } catch (Exception e) {
            log.error("하원 기록 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "하원 기록에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/attendance/daily?classroomId=" + classroomId + "&date=" + date;
    }

    /**
     * 결석 처리
     */
    @PostMapping("/attendance/kid/{kidId}/absent")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'TEACHER')")
    public String markAbsent(
            @PathVariable Long kidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long classroomId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            attendanceService.markAbsent(kidId, date, note, userDetails.getMemberId());
            redirectAttributes.addFlashAttribute("message", "결석 처리되었습니다.");
        } catch (Exception e) {
            log.error("결석 처리 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("error", "결석 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/attendance/daily?classroomId=" + classroomId + "&date=" + date;
    }
}
