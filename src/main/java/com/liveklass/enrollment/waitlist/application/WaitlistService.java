package com.liveklass.enrollment.waitlist.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;
import com.liveklass.enrollment.waitlist.infrastructure.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final LectureRepository lectureRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    /**
     * 대기열 등록 (O2). OPEN 강의에 한해, 이미 active 신청이 없고 대기열에도 없을 때.
     * (uq_waitlist_user_lecture UNIQUE 제약이 중복 등록 경합의 최종 방어선)
     */
    @Transactional
    public WaitlistEntry join(Long userId, Long lectureId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "존재하지 않는 사용자: " + userId);
        }
        Lecture lecture = lectureRepository.findById(lectureId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + lectureId));
        if (!lecture.getStatus().isOpenForEnrollment()) {
            throw new BusinessException(ErrorCode.LECTURE_NOT_OPEN, "현재 강의 상태: " + lecture.getStatus());
        }
        if (enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(userId, lectureId, EnrollmentStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT, "이미 신청한 강의는 대기열에 등록할 수 없습니다.");
        }
        if (waitlistRepository.existsByUserIdAndLectureId(userId, lectureId)) {
            throw new BusinessException(ErrorCode.ALREADY_IN_WAITLIST);
        }
        return waitlistRepository.save(new WaitlistEntry(userId, lectureId));
    }

    /** 강의별 대기열 조회 — 강의 작성 크리에이터만 (등록 순서 FIFO). */
    @Transactional(readOnly = true)
    public Page<WaitlistEntry> findByLecture(Long requesterId, Long lectureId, Pageable pageable) {
        Lecture lecture = lectureRepository.findById(lectureId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + lectureId));
        if (!lecture.getCreatorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.NOT_LECTURE_OWNER);
        }
        return waitlistRepository.findByLectureId(lectureId, pageable);
    }
}
