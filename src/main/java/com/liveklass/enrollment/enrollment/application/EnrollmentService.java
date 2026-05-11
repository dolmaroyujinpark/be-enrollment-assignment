package com.liveklass.enrollment.enrollment.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;

    /**
     * 수강 신청 (PENDING 생성).
     * - 강의가 OPEN 상태일 때만 (BR-1)
     * - 동일 강의에 대한 active 신청은 1개만 (BR-3, 부분 UNIQUE 인덱스가 최종 방어선)
     * - 정원 = 활성(PENDING+CONFIRMED) 수, 초과 시 거부 (BR-7 / F9)
     * - Lecture row 에 비관 락을 걸어 마지막 자리 동시 신청을 직렬화 (F10)
     */
    @Transactional
    public Enrollment apply(Long userId, Long lectureId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "존재하지 않는 사용자: " + userId);
        }

        Lecture lecture = lectureRepository.findByIdForUpdate(lectureId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + lectureId));

        if (!lecture.getStatus().isOpenForEnrollment()) {
            throw new BusinessException(ErrorCode.LECTURE_NOT_OPEN, "현재 강의 상태: " + lecture.getStatus());
        }
        if (enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(userId, lectureId, EnrollmentStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
        }
        if (!lecture.hasAvailableSeat()) {
            throw new BusinessException(ErrorCode.CAPACITY_EXCEEDED,
                "정원 %d명이 모두 찼습니다.".formatted(lecture.getCapacity()));
        }

        lecture.incrementEnrolled();
        return enrollmentRepository.save(new Enrollment(userId, lectureId));
    }

    /**
     * 수강 취소 (→ CANCELLED).
     * - 본인 신청만 취소 가능 (BR-10)
     * - 활성 신청이 취소되면 강의 정원 카운터를 1 감소 (Lecture row 비관 락으로 직렬화)
     * - 취소 가능 기간 제한(O1)은 선택 구현이라 여기서는 적용하지 않음 (#9 에서 도입)
     */
    @Transactional
    public Enrollment cancel(Long userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND, "존재하지 않는 수강 신청: " + enrollmentId));
        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_ENROLLMENT_OWNER);
        }

        Lecture lecture = lectureRepository.findByIdForUpdate(enrollment.getLectureId())
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + enrollment.getLectureId()));

        boolean wasActive = enrollment.getStatus().isActive();
        try {
            enrollment.cancel(Instant.now());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_ENROLLMENT_STATUS_TRANSITION, e.getMessage());
        }
        if (wasActive) {
            lecture.decrementEnrolled();
        }
        return enrollment;
    }

    /** 내 수강 신청 목록 (페이지네이션). */
    @Transactional(readOnly = true)
    public Page<Enrollment> findMine(Long userId, Pageable pageable) {
        return enrollmentRepository.findByUserId(userId, pageable);
    }
}
