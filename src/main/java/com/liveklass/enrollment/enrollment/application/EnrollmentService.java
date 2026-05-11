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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;

    /** CONFIRMED 신청의 취소 가능 기간 (O1). null 이면 제한 없음. application.yml 의 enrollment.refund-window 로 설정. */
    @Value("${enrollment.refund-window:7d}")
    private Duration refundWindow;

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
     * - CONFIRMED 신청은 결제 후 refundWindow(기본 7일) 이내에만 취소 가능 (BR-6 / O1). PENDING 은 기간 제한 없음
     * - 활성 신청이 취소되면 강의 정원 카운터를 1 감소 (Lecture row 비관 락으로 직렬화)
     */
    @Transactional
    public Enrollment cancel(Long userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND, "존재하지 않는 수강 신청: " + enrollmentId));
        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_ENROLLMENT_OWNER);
        }
        ensureWithinRefundWindow(enrollment);

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

    private void ensureWithinRefundWindow(Enrollment enrollment) {
        if (refundWindow == null
            || enrollment.getStatus() != EnrollmentStatus.CONFIRMED
            || enrollment.getConfirmedAt() == null) {
            return;
        }
        Instant deadline = enrollment.getConfirmedAt().plus(refundWindow);
        if (Instant.now().isAfter(deadline)) {
            throw new BusinessException(ErrorCode.REFUND_WINDOW_PASSED,
                "결제 후 %d일이 지나 취소할 수 없습니다.".formatted(refundWindow.toDays()));
        }
    }

    /** 내 수강 신청 목록 (페이지네이션). */
    @Transactional(readOnly = true)
    public Page<Enrollment> findMine(Long userId, Pageable pageable) {
        return enrollmentRepository.findByUserId(userId, pageable);
    }

    /** 강의별 수강생 목록 (페이지네이션) — 강의 작성 크리에이터만 조회 가능 (BR-11 / O3). */
    @Transactional(readOnly = true)
    public Page<Enrollment> findByLecture(Long requesterId, Long lectureId, Pageable pageable) {
        Lecture lecture = lectureRepository.findById(lectureId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + lectureId));
        if (!lecture.getCreatorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.NOT_LECTURE_OWNER);
        }
        return enrollmentRepository.findByLectureId(lectureId, pageable);
    }
}
