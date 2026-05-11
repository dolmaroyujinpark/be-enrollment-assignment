package com.liveklass.enrollment.enrollment.presentation.dto;

import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;

import java.time.Instant;

public record EnrollmentResponse(
    Long id,
    Long userId,
    Long lectureId,
    EnrollmentStatus status,
    Instant appliedAt,
    Instant confirmedAt,
    Instant cancelledAt,
    Long paymentIntentId
) {
    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(
            e.getId(),
            e.getUserId(),
            e.getLectureId(),
            e.getStatus(),
            e.getAppliedAt(),
            e.getConfirmedAt(),
            e.getCancelledAt(),
            e.getPaymentIntentId()
        );
    }
}
