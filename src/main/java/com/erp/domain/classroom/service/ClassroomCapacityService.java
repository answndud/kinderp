package com.erp.domain.classroom.service;

import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.classroom.repository.ClassroomRepository;
import com.erp.domain.kid.repository.KidRepository;
import com.erp.domain.kidapplication.entity.ApplicationStatus;
import com.erp.domain.kidapplication.repository.KidApplicationRepository;
import com.erp.global.exception.BusinessException;
import com.erp.global.exception.ErrorCode;
import com.erp.global.common.ProductTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomCapacityService {

    private final ClassroomRepository classroomRepository;
    private final KidRepository kidRepository;
    private final KidApplicationRepository kidApplicationRepository;

    public record ClassroomSeatSummary(
            Long classroomId,
            int capacity,
            long enrolledKidCount,
            long reservedOfferCount,
            long availableSeatCount
    ) {
    }

    public Classroom lockClassroom(Long classroomId) {
        return classroomRepository.findByIdAndDeletedAtIsNullForUpdate(classroomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASSROOM_NOT_FOUND));
    }

    public ClassroomSeatSummary summarize(Classroom classroom) {
        long enrolledKidCount = kidRepository.countByClassroomIdAndDeletedAtIsNull(classroom.getId());
        long reservedOfferCount = kidApplicationRepository.countActiveOffersByAssignedClassroomId(
                classroom.getId(),
                ApplicationStatus.OFFERED,
                ProductTime.nowDateTime()
        );
        long availableSeatCount = classroom.remainingSeats(enrolledKidCount + reservedOfferCount);
        return new ClassroomSeatSummary(
                classroom.getId(),
                classroom.getCapacity(),
                enrolledKidCount,
                reservedOfferCount,
                availableSeatCount
        );
    }

    public void validateSeatAvailable(Classroom classroom) {
        ClassroomSeatSummary summary = summarize(classroom);
        if (summary.availableSeatCount() <= 0) {
            throw new BusinessException(ErrorCode.CLASSROOM_CAPACITY_EXCEEDED);
        }
    }

    public void validateCapacityReduction(Classroom classroom, int requestedCapacity) {
        ClassroomSeatSummary summary = summarize(classroom);
        long occupiedSeats = summary.enrolledKidCount() + summary.reservedOfferCount();
        if (!classroom.canResizeTo(occupiedSeats, requestedCapacity)) {
            throw new BusinessException(ErrorCode.CLASSROOM_CAPACITY_REDUCTION_NOT_ALLOWED);
        }
    }
}
