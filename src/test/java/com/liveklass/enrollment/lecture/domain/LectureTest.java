package com.liveklass.enrollment.lecture.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LectureTest {

    private static Lecture sampleLecture() {
        return new Lecture(
            1L,
            "Spring Boot 백엔드",
            "JPA · 트랜잭션 · 동시성",
            new BigDecimal("199000"),
            10,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 1)
        );
    }

    @Nested
    @DisplayName("강의 생성")
    class Creation {

        @Test
        @DisplayName("정상 생성 시 상태는 DRAFT, enrolledCount는 0")
        void initialState() {
            Lecture lecture = sampleLecture();

            assertThat(lecture.getStatus()).isEqualTo(LectureStatus.DRAFT);
            assertThat(lecture.getEnrolledCount()).isZero();
            assertThat(lecture.hasAvailableSeat()).isTrue();
        }

        @Test
        @DisplayName("capacity 가 0 이하이면 거부")
        void rejectNonPositiveCapacity() {
            assertThatThrownBy(() -> new Lecture(
                1L, "T", "D", BigDecimal.ZERO, 0,
                LocalDate.now(), LocalDate.now().plusDays(1)
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("price 가 음수이면 거부")
        void rejectNegativePrice() {
            assertThatThrownBy(() -> new Lecture(
                1L, "T", "D", new BigDecimal("-1"), 5,
                LocalDate.now(), LocalDate.now().plusDays(1)
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("startDate 가 endDate 보다 늦으면 거부")
        void rejectInvalidDateRange() {
            assertThatThrownBy(() -> new Lecture(
                1L, "T", "D", BigDecimal.TEN, 5,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 6, 1)
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이 (FSM)")
    class StatusTransition {

        @Test
        @DisplayName("DRAFT → OPEN 허용")
        void draftToOpen() {
            Lecture lecture = sampleLecture();

            lecture.changeStatus(LectureStatus.OPEN);

            assertThat(lecture.getStatus()).isEqualTo(LectureStatus.OPEN);
        }

        @Test
        @DisplayName("OPEN → CLOSED 허용")
        void openToClosed() {
            Lecture lecture = sampleLecture();
            lecture.changeStatus(LectureStatus.OPEN);

            lecture.changeStatus(LectureStatus.CLOSED);

            assertThat(lecture.getStatus()).isEqualTo(LectureStatus.CLOSED);
        }

        @Test
        @DisplayName("DRAFT → CLOSED 차단 (단계 건너뛰기 금지)")
        void rejectDraftToClosed() {
            Lecture lecture = sampleLecture();

            assertThatThrownBy(() -> lecture.changeStatus(LectureStatus.CLOSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid status transition");
        }

        @Test
        @DisplayName("OPEN → DRAFT 차단 (역전이 금지)")
        void rejectOpenToDraft() {
            Lecture lecture = sampleLecture();
            lecture.changeStatus(LectureStatus.OPEN);

            assertThatThrownBy(() -> lecture.changeStatus(LectureStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CLOSED 에서 어떤 상태로도 전이 불가 (재오픈 금지)")
        void closedIsTerminal() {
            Lecture lecture = sampleLecture();
            lecture.changeStatus(LectureStatus.OPEN);
            lecture.changeStatus(LectureStatus.CLOSED);

            assertThatThrownBy(() -> lecture.changeStatus(LectureStatus.OPEN))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> lecture.changeStatus(LectureStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("정원 카운터 가드")
    class EnrolledCount {

        @Test
        @DisplayName("incrementEnrolled 는 capacity 까지 가능")
        void incrementUpToCapacity() {
            Lecture lecture = sampleLecture();

            for (int i = 0; i < 10; i++) {
                lecture.incrementEnrolled();
            }

            assertThat(lecture.getEnrolledCount()).isEqualTo(10);
            assertThat(lecture.hasAvailableSeat()).isFalse();
        }

        @Test
        @DisplayName("capacity 도달 후 incrementEnrolled 호출 시 거부")
        void rejectIncrementBeyondCapacity() {
            Lecture lecture = sampleLecture();
            for (int i = 0; i < 10; i++) {
                lecture.incrementEnrolled();
            }

            assertThatThrownBy(lecture::incrementEnrolled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity exceeded");
        }

        @Test
        @DisplayName("decrementEnrolled 가 0 미만으로 가지 않음")
        void rejectDecrementBelowZero() {
            Lecture lecture = sampleLecture();

            assertThatThrownBy(lecture::decrementEnrolled)
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
