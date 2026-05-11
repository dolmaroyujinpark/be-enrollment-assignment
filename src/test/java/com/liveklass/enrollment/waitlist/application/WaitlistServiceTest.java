package com.liveklass.enrollment.waitlist.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;
import com.liveklass.enrollment.waitlist.infrastructure.WaitlistRepository;
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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    private static final long USER_ID = 1L;
    private static final long LECTURE_ID = 2L;
    private static final long CREATOR_ID = 10L;

    @Mock WaitlistRepository waitlistRepository;
    @Mock LectureRepository lectureRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock UserRepository userRepository;

    @InjectMocks WaitlistService waitlistService;

    private static Lecture lecture(LectureStatus status) {
        return lecture(status, 5, 0);
    }

    private static Lecture lecture(LectureStatus status, int capacity, int enrolled) {
        Lecture l = new Lecture(CREATOR_ID, "T", "D", new BigDecimal("10000"), capacity,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        if (status != LectureStatus.DRAFT) {
            l.changeStatus(LectureStatus.OPEN);
        }
        for (int i = 0; i < enrolled; i++) {
            l.incrementEnrolled();
        }
        injectId(l, LECTURE_ID);
        return l;
    }

    private static void injectId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("대기열 등록 join")
    class Join {

        @Test
        @DisplayName("OPEN 강의에 정상 등록")
        void happyPath() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN)));
            given(enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(USER_ID, LECTURE_ID, EnrollmentStatus.CANCELLED))
                .willReturn(false);
            given(waitlistRepository.existsByUserIdAndLectureId(USER_ID, LECTURE_ID)).willReturn(false);
            given(waitlistRepository.save(any(WaitlistEntry.class))).willAnswer(inv -> inv.getArgument(0));

            WaitlistEntry result = waitlistService.join(USER_ID, LECTURE_ID);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getLectureId()).isEqualTo(LECTURE_ID);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 → USER_NOT_FOUND")
        void userNotFound() {
            given(userRepository.existsById(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> waitlistService.join(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 강의 → LECTURE_NOT_FOUND")
        void lectureNotFound() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.join(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LECTURE_NOT_FOUND);
        }

        @Test
        @DisplayName("OPEN 이 아닌 강의 → LECTURE_NOT_OPEN")
        void lectureNotOpen() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.DRAFT)));

            assertThatThrownBy(() -> waitlistService.join(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LECTURE_NOT_OPEN);
        }

        @Test
        @DisplayName("이미 신청(active)한 강의면 → DUPLICATE_ENROLLMENT")
        void alreadyEnrolled() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN)));
            given(enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(USER_ID, LECTURE_ID, EnrollmentStatus.CANCELLED))
                .willReturn(true);

            assertThatThrownBy(() -> waitlistService.join(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_ENROLLMENT);
        }

        @Test
        @DisplayName("이미 대기열에 있으면 → ALREADY_IN_WAITLIST")
        void alreadyInWaitlist() {
            given(userRepository.existsById(USER_ID)).willReturn(true);
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN)));
            given(enrollmentRepository.existsByUserIdAndLectureIdAndStatusNot(USER_ID, LECTURE_ID, EnrollmentStatus.CANCELLED))
                .willReturn(false);
            given(waitlistRepository.existsByUserIdAndLectureId(USER_ID, LECTURE_ID)).willReturn(true);

            assertThatThrownBy(() -> waitlistService.join(USER_ID, LECTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_IN_WAITLIST);
        }
    }

    @Nested
    @DisplayName("강의별 대기열 조회 findByLecture")
    class FindByLecture {

        private final Pageable pageable = PageRequest.of(0, 50);

        @Test
        @DisplayName("강의 작성 크리에이터가 조회하면 대기열 페이지 반환")
        void byCreator() {
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN)));
            given(waitlistRepository.findByLectureId(LECTURE_ID, pageable))
                .willReturn(new PageImpl<>(List.of(new WaitlistEntry(USER_ID, LECTURE_ID))));

            Page<WaitlistEntry> result = waitlistService.findByLecture(CREATOR_ID, LECTURE_ID, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("작성자가 아닌 사용자가 조회하면 → NOT_LECTURE_OWNER")
        void notCreator() {
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.of(lecture(LectureStatus.OPEN)));

            assertThatThrownBy(() -> waitlistService.findByLecture(999L, LECTURE_ID, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_LECTURE_OWNER);
        }

        @Test
        @DisplayName("존재하지 않는 강의 → LECTURE_NOT_FOUND")
        void lectureNotFound() {
            given(lectureRepository.findById(LECTURE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.findByLecture(CREATOR_ID, LECTURE_ID, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LECTURE_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("removeIfPresent 는 대기열 항목 삭제를 위임한다")
    void removeIfPresent() {
        waitlistService.removeIfPresent(USER_ID, LECTURE_ID);

        verify(waitlistRepository).deleteByUserIdAndLectureId(USER_ID, LECTURE_ID);
    }

    @Nested
    @DisplayName("[P3] 대기열 자동 승급 promoteNext")
    class PromoteNext {

        @Test
        @DisplayName("자리가 있고 대기열에 사람이 있으면 head 를 PENDING 으로 승급 + 정원 카운터 +1 + 대기열 항목 삭제")
        void promotesHead() {
            Lecture lecture = lecture(LectureStatus.OPEN, 5, 0);
            WaitlistEntry head = new WaitlistEntry(USER_ID, LECTURE_ID);
            given(waitlistRepository.findNextInQueueForUpdate(LECTURE_ID)).willReturn(Optional.of(head));
            given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(inv -> inv.getArgument(0));

            Optional<Enrollment> promoted = waitlistService.promoteNext(lecture);

            assertThat(promoted).isPresent();
            assertThat(promoted.get().getUserId()).isEqualTo(USER_ID);
            assertThat(promoted.get().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(lecture.getEnrolledCount()).isEqualTo(1);
            verify(waitlistRepository).delete(head);
        }

        @Test
        @DisplayName("대기열이 비어 있으면 아무 일도 일어나지 않음")
        void emptyQueue() {
            Lecture lecture = lecture(LectureStatus.OPEN, 5, 0);
            given(waitlistRepository.findNextInQueueForUpdate(LECTURE_ID)).willReturn(Optional.empty());

            assertThat(waitlistService.promoteNext(lecture)).isEmpty();
            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("빈 자리가 없으면 대기열을 조회하지도 않음")
        void noAvailableSeat() {
            Lecture lecture = lecture(LectureStatus.OPEN, 1, 1); // 정원 1, 1명 등록 → 자리 없음

            assertThat(waitlistService.promoteNext(lecture)).isEmpty();
            verify(waitlistRepository, never()).findNextInQueueForUpdate(anyLong());
        }
    }
}
