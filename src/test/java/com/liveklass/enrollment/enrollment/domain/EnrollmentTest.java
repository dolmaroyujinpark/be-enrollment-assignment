package com.liveklass.enrollment.enrollment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static Enrollment pending() {
        return new Enrollment(1L, 2L);
    }

    private static Enrollment confirmed() {
        Enrollment e = pending();
        e.confirm(99L, CONFIRMED_AT);
        return e;
    }

    @Test
    @DisplayName("생성 직후 상태는 PENDING")
    void initialState() {
        assertThat(pending().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Nested
    @DisplayName("결제 확정 (confirm)")
    class Confirm {

        @Test
        @DisplayName("PENDING → CONFIRMED, confirmedAt·paymentIntentId 설정")
        void pendingToConfirmed() {
            Enrollment e = pending();

            e.confirm(99L, CONFIRMED_AT);

            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(e.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
            assertThat(e.getPaymentIntentId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("이미 CONFIRMED 면 다시 확정 불가")
        void rejectConfirmAgain() {
            Enrollment e = confirmed();

            assertThatThrownBy(() -> e.confirm(100L, CONFIRMED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CANCELLED 면 확정 불가")
        void rejectConfirmAfterCancel() {
            Enrollment e = pending();
            e.cancel(CONFIRMED_AT);

            assertThatThrownBy(() -> e.confirm(100L, CONFIRMED_AT))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("취소 (cancel)")
    class Cancel {

        @Test
        @DisplayName("PENDING → CANCELLED, cancelledAt 설정")
        void pendingToCancelled() {
            Enrollment e = pending();
            Instant now = Instant.parse("2026-02-01T00:00:00Z");

            e.cancel(now);

            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(e.getCancelledAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("CONFIRMED 도 기간 제한 없이 취소 가능")
        void confirmedToCancelledNoWindow() {
            Enrollment e = confirmed();

            e.cancel(CONFIRMED_AT.plus(Duration.ofDays(365)));

            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED + refundWindow 7일: 기간 내 취소 가능")
        void confirmedWithinRefundWindow() {
            Enrollment e = confirmed();

            e.cancel(CONFIRMED_AT.plus(Duration.ofDays(3)), Duration.ofDays(7));

            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED + refundWindow 7일: 기간 경과 시 취소 불가")
        void confirmedAfterRefundWindow() {
            Enrollment e = confirmed();

            assertThatThrownBy(() -> e.cancel(CONFIRMED_AT.plus(Duration.ofDays(8)), Duration.ofDays(7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refund window");
        }

        @Test
        @DisplayName("이미 CANCELLED 면 다시 취소 불가")
        void rejectCancelAgain() {
            Enrollment e = pending();
            e.cancel(CONFIRMED_AT);

            assertThatThrownBy(() -> e.cancel(CONFIRMED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
