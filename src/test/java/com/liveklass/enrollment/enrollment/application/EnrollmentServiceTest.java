package com.liveklass.enrollment.enrollment.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import com.liveklass.enrollment.waitlist.application.WaitlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final long USER_ID = 1L;
    private static final long LECTURE_ID = 2L;
    private static final long ENROLLMENT_ID = 5L;

    @Mock EnrollmentRepository enrollmentRepository;
    @Mock LectureRepository lectureRepository;
    @Mock UserRepository userRepository;
    @Mock WaitlistService waitlistService;

    @InjectMocks EnrollmentService enrollmentService;

    private static Lecture lecture(LectureStatus status, int capacity, int enrolled) {
        Lecture l = new Lecture(10L, "T", "D", new BigDecimal("10000"), capacity,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        if (status != LectureStatus.DRAFT) {
            l.changeStatus(LectureStatus.OPEN);
        }
        if (status == LectureStatus.CLOSED) {
            l.changeStatus(LectureStatus.CLOSED);
        }
        for (int i = 0; i < enrolled; i++) {
            l.incrementEnrolled();
        }
        return l;
    }

    @Nested
    @DisplayName("수강 신청 apply")
    class Apply {

        @Test
        @DisplayName("정상 신청 시 PENDING 생성 + 정원 카운터 +1")
        void happyPath() {
            Lecture lecture = lecture(LectureStatus.OPEN, 5, 0);
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture));
            given(enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(USER_ID, LECTURE_ID, EnrollmentStatus.CANCELLED))
                .willReturn(false);
            given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(inv -> inv.getArgument(0));

            Enrollment result = enrollmentService.apply(USER_ID, LECTURE_ID);

            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getLectureId()).isEqualTo(LECTURE_ID);
            assertThat(lecture.getEnrolledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 → USER_NOT_FOUND")
        void userNotFound() {
            given(userRepository.existsById(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> enrollmentService.apply(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 강의 → LECTURE_NOT_FOUND")
        void lectureNotFound() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.apply(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LECTURE_NOT_FOUND);
        }

        @Test
        @DisplayName("OPEN 이 아닌 강의 → LECTURE_NOT_OPEN")
        void lectureNotOpen() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.DRAFT, 5, 0)));

            assertThatThrownBy(() -> enrollmentService.apply(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LECTURE_NOT_OPEN);
        }

        @Test
        @DisplayName("동일 강의에 active 신청이 이미 있으면 → DUPLICATE_ENROLLMENT")
        void duplicate() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN, 5, 0)));
            given(enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(USER_ID, LECTURE_ID, EnrollmentStatus.CANCELLED))
                .willReturn(true);

            assertThatThrownBy(() -> enrollmentService.apply(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_ENROLLMENT);
        }

        @Test
        @DisplayName("정원이 다 차면 → CAPACITY_EXCEEDED")
        void capacityFull() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN, 1, 1)));
            given(enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(USER_ID, LECTURE_ID, EnrollmentStatus.CANCELLED))
                .willReturn(false);

            assertThatThrownBy(() -> enrollmentService.apply(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CAPACITY_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("수강 취소 cancel")
    class Cancel {

        @Test
        @DisplayName("본인 신청 취소 시 CANCELLED + 정원 카운터 -1")
        void happyPath() {
            Enrollment enrollment = new Enrollment(USER_ID, LECTURE_ID);
            Lecture lecture = lecture(LectureStatus.OPEN, 5, 1);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture));

            Enrollment result = enrollmentService.cancel(USER_ID, ENROLLMENT_ID);

            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(lecture.getEnrolledCount()).isZero();
        }

        @Test
        @DisplayName("[P3] 활성 신청 취소 시 대기열 자동 승급(promoteNext) 호출")
        void promotesWaitlistOnCancel() {
            Enrollment enrollment = new Enrollment(USER_ID, LECTURE_ID);
            Lecture lecture = lecture(LectureStatus.OPEN, 5, 1);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture));

            enrollmentService.cancel(USER_ID, ENROLLMENT_ID);

            verify(waitlistService).promoteNext(lecture);
        }

        @Test
        @DisplayName("존재하지 않는 신청 → ENROLLMENT_NOT_FOUND")
        void notFound() {
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("타인의 신청을 취소하면 → NOT_ENROLLMENT_OWNER")
        void notOwner() {
            Enrollment enrollment = new Enrollment(USER_ID, LECTURE_ID);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancel(999L, ENROLLMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ENROLLMENT_OWNER);
        }

        @Test
        @DisplayName("이미 취소된 신청을 다시 취소하면 → INVALID_ENROLLMENT_STATUS_TRANSITION")
        void alreadyCancelled() {
            Enrollment enrollment = new Enrollment(USER_ID, LECTURE_ID);
            enrollment.cancel(Instant.now());
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN, 5, 0)));

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ENROLLMENT_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("[O1] CONFIRMED 신청도 결제 후 7일 이내면 취소 가능")
        void confirmedWithinRefundWindow() {
            ReflectionTestUtils.setField(enrollmentService, "refundWindow", Duration.ofDays(7));
            Enrollment enrollment = new Enrollment(USER_ID, LECTURE_ID);
            enrollment.confirm(99L, Instant.now().minus(Duration.ofDays(3)));
            Lecture lecture = lecture(LectureStatus.OPEN, 5, 1);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));
            given(lectureRepository.findByIdForUpdate(LECTURE_ID)).willReturn(Optional.of(lecture));

            Enrollment result = enrollmentService.cancel(USER_ID, ENROLLMENT_ID);

            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(lecture.getEnrolledCount()).isZero();
        }

        @Test
        @DisplayName("[O1] CONFIRMED 신청을 결제 후 7일 경과 후 취소하면 → REFUND_WINDOW_PASSED")
        void confirmedAfterRefundWindow() {
            ReflectionTestUtils.setField(enrollmentService, "refundWindow", Duration.ofDays(7));
            Enrollment enrollment = new Enrollment(USER_ID, LECTURE_ID);
            enrollment.confirm(99L, Instant.now().minus(Duration.ofDays(8)));
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_WINDOW_PASSED);
        }
    }

    @Nested
    @DisplayName("[O3] 강의별 수강생 목록 findByLecture")
    class FindByLecture {

        private static final long CREATOR_ID = 10L; // lecture() 헬퍼가 생성하는 강의의 creatorId
        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("강의 작성 크리에이터가 조회하면 수강생 페이지 반환")
        void byCreator() {
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN, 5, 0)));
            given(enrollmentRepository.findByLectureId(LECTURE_ID, pageable))
                .willReturn(new PageImpl<>(List.of(new Enrollment(1L, LECTURE_ID))));

            Page<Enrollment> result = enrollmentService.findByLecture(CREATOR_ID, LECTURE_ID, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("작성자가 아닌 사용자가 조회하면 → NOT_LECTURE_OWNER")
        void notCreator() {
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN, 5, 0)));

            assertThatThrownBy(() -> enrollmentService.findByLecture(999L, LECTURE_ID, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_LECTURE_OWNER);
        }

        @Test
        @DisplayName("존재하지 않는 강의 → LECTURE_NOT_FOUND")
        void lectureNotFound() {
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.findByLecture(CREATOR_ID, LECTURE_ID, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LECTURE_NOT_FOUND);
        }
    }
}
