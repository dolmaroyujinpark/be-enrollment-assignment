package com.liveklass.enrollment.payment.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.payment.domain.PaymentIntent;
import com.liveklass.enrollment.payment.domain.PaymentIntentStatus;
import com.liveklass.enrollment.payment.infrastructure.PaymentIntentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    private static final long USER_ID = 1L;
    private static final long ENROLLMENT_ID = 5L;
    private static final long PAYMENT_INTENT_ID = 100L;
    private static final String KEY = "idem-key-1";

    @Mock PaymentIntentRepository paymentIntentRepository;
    @Mock EnrollmentRepository enrollmentRepository;

    @InjectMocks PaymentConfirmService paymentConfirmService;

    private static Enrollment enrollmentWithId(long id, long userId) {
        Enrollment e = new Enrollment(userId, 2L);
        injectId(e, id);
        return e;
    }

    private static void injectId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("정상 결제 확정 시 PENDING → CONFIRMED, PaymentIntent 는 COMPLETED")
    void confirm_success() {
        Enrollment enrollment = enrollmentWithId(ENROLLMENT_ID, USER_ID);
        given(paymentIntentRepository.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));
        given(paymentIntentRepository.save(any(PaymentIntent.class))).willAnswer(inv -> {
            PaymentIntent intent = inv.getArgument(0);
            injectId(intent, PAYMENT_INTENT_ID);
            return intent;
        });

        Enrollment result = paymentConfirmService.confirm(USER_ID, ENROLLMENT_ID, KEY);

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(result.getPaymentIntentId()).isEqualTo(PAYMENT_INTENT_ID);

        ArgumentCaptor<PaymentIntent> captor = ArgumentCaptor.forClass(PaymentIntent.class);
        verify(paymentIntentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentIntentStatus.COMPLETED);
        assertThat(captor.getValue().getEnrollmentId()).isEqualTo(ENROLLMENT_ID);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 재호출하면 상태 변경 없이 동일 결과 반환 (멱등)")
    void confirm_idempotentRetry() {
        Enrollment alreadyConfirmed = enrollmentWithId(ENROLLMENT_ID, USER_ID);
        alreadyConfirmed.confirm(PAYMENT_INTENT_ID, Instant.now());
        given(paymentIntentRepository.findByIdempotencyKey(KEY))
            .willReturn(Optional.of(new PaymentIntent(KEY, ENROLLMENT_ID)));
        given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(alreadyConfirmed));

        Enrollment result = paymentConfirmService.confirm(USER_ID, ENROLLMENT_ID, KEY);

        assertThat(result).isSameAs(alreadyConfirmed);
        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        verify(paymentIntentRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 다른 신청에 쓰인 Idempotency-Key → IDEMPOTENCY_KEY_CONFLICT")
    void confirm_keyConflict() {
        long otherEnrollmentId = 999L;
        given(paymentIntentRepository.findByIdempotencyKey(KEY))
            .willReturn(Optional.of(new PaymentIntent(KEY, otherEnrollmentId)));
        given(enrollmentRepository.findById(otherEnrollmentId))
            .willReturn(Optional.of(enrollmentWithId(otherEnrollmentId, USER_ID)));

        assertThatThrownBy(() -> paymentConfirmService.confirm(USER_ID, ENROLLMENT_ID, KEY))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("존재하지 않는 신청 → ENROLLMENT_NOT_FOUND")
    void confirm_enrollmentNotFound() {
        given(paymentIntentRepository.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentConfirmService.confirm(USER_ID, ENROLLMENT_ID, KEY))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENROLLMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("타인의 신청을 결제하면 → NOT_ENROLLMENT_OWNER")
    void confirm_notOwner() {
        given(paymentIntentRepository.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollmentWithId(ENROLLMENT_ID, USER_ID)));

        assertThatThrownBy(() -> paymentConfirmService.confirm(999L, ENROLLMENT_ID, KEY))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ENROLLMENT_OWNER);
    }

    @Test
    @DisplayName("PENDING 이 아닌 신청에 결제 시도 → INVALID_ENROLLMENT_STATUS_TRANSITION")
    void confirm_notPending() {
        Enrollment alreadyConfirmed = enrollmentWithId(ENROLLMENT_ID, USER_ID);
        alreadyConfirmed.confirm(PAYMENT_INTENT_ID, Instant.now());
        given(paymentIntentRepository.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(alreadyConfirmed));
        given(paymentIntentRepository.save(any(PaymentIntent.class))).willAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> paymentConfirmService.confirm(USER_ID, ENROLLMENT_ID, KEY))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ENROLLMENT_STATUS_TRANSITION);
    }
}
