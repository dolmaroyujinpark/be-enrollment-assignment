package com.liveklass.enrollment.waitlist.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
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

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final LectureRepository lectureRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    /**
     * 대기열 등록 (O2). OPEN 이면서 정원이 모두 찬 강의에 한해, 이미 active 신청이 없고 대기열에도 없을 때.
     * 자리가 남아있으면 대기열 등록이 아니라 바로 수강 신청하도록 안내한다.
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
        // 정책 변경: 정원이 남아있으면 대기열 등록을 거부하고 직접 신청을 유도한다.
        if (lecture.hasAvailableSeat()) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_NEEDED,
                "남은 자리 %d석 — 대기열 대신 POST /api/enrollments 로 신청하세요.".formatted(
                    lecture.getCapacity() - lecture.getEnrolledCount()));
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

    /** 신청 시 사용자의 대기열 항목 제거 (있으면) — 동일 강의에 enrollment + waitlist 가 공존하지 않도록. */
    @Transactional
    public void removeIfPresent(Long userId, Long lectureId) {
        waitlistRepository.deleteByUserIdAndLectureId(userId, lectureId);
    }

    /**
     * 한 자리가 비면 대기열의 가장 오래된(FIFO) 사람을 PENDING 신청으로 자동 승급 (P3).
     * FOR UPDATE SKIP LOCKED 로 대기열 head 를 한 명만 안전하게 잡는다.
     * 호출자(취소 처리)는 이미 해당 lecture row 에 비관 락을 잡은 상태여야 한다 (정원 카운터 정합).
     */
    @Transactional
    public Optional<Enrollment> promoteNext(Lecture lecture) {
        // 명세 §1 — CLOSED 는 "신청불가" 상태. 자동 승급도 신청을 만드는 행위이므로 OPEN 일 때만 허용한다.
        if (!lecture.getStatus().isOpenForEnrollment()) {
            return Optional.empty();
        }
        if (!lecture.hasAvailableSeat()) {
            return Optional.empty();
        }
        Optional<WaitlistEntry> head = waitlistRepository.findNextInQueueForUpdate(lecture.getId());
        if (head.isEmpty()) {
            return Optional.empty();
        }
        WaitlistEntry entry = head.get();
        waitlistRepository.delete(entry);
        lecture.incrementEnrolled();
        return Optional.of(enrollmentRepository.save(new Enrollment(entry.getUserId(), lecture.getId())));
    }
}
