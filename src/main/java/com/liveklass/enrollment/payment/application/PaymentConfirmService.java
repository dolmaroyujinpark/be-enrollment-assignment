package com.liveklass.enrollment.payment.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.payment.domain.PaymentIntent;
import com.liveklass.enrollment.payment.infrastructure.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
     * 결제 확정 (PENDING → CONFIRMED). 외부 PG 연동 없이 상태 변경으로 대체 (명세).
     * Idempotency-Key 헤더로 멱등성 보장 — 같은 키로 다시 호출하면 상태 변경 없이 동일 결과 반환.
     * (드물게 동일 키 동시 요청이 경합하면 payment_intents.idempotency_key UNIQUE 제약이 최종 방어선 → 409)
     */
    @Transactional
    public Enrollment confirm(Long userId, Long enrollmentId, String idempotencyKey) {
        Optional<PaymentIntent> alreadyProcessed = paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
        if (alreadyProcessed.isPresent()) {
            Enrollment enrollment = loadEnrollment(alreadyProcessed.get().getEnrollmentId());
            if (!enrollment.getId().equals(enrollmentId)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return enrollment; // 멱등: 이미 처리됨, 동일 응답
        }

        Enrollment enrollment = loadEnrollment(enrollmentId);
        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_ENROLLMENT_OWNER, "본인의 수강 신청만 결제할 수 있습니다.");
        }

        PaymentIntent intent = paymentIntentRepository.save(new PaymentIntent(idempotencyKey, enrollmentId));
        try {
            enrollment.confirm(intent.getId(), Instant.now());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_ENROLLMENT_STATUS_TRANSITION, e.getMessage());
        }
        intent.markCompleted();
        return enrollment;
    }

    private Enrollment loadEnrollment(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND, "존재하지 않는 수강 신청: " + enrollmentId));
    }
}
