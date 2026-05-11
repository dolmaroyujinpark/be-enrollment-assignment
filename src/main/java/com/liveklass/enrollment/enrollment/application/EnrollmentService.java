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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
